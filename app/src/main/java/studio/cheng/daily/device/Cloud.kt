package studio.cheng.daily.device

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import android.util.Base64
import org.json.JSONObject
import studio.cheng.daily.BuildConfig
import studio.cheng.daily.data.Digest
import studio.cheng.daily.data.MealReading
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.monthDates
import studio.cheng.daily.data.spanScore
import studio.cheng.daily.data.todayKey
import studio.cheng.daily.data.weekDates
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class CloudUser(
    val id: Long,
    val email: String,
    val phone: String,
    val name: String,
)

data class RankRow(val place: Int, val name: String, val score: Int, val mine: Boolean)

class CloudError(message: String) : Exception(message)

object Session {
    private const val PREF = "cheng.cloud"
    private val _user = MutableStateFlow<CloudUser?>(null)
    val user: StateFlow<CloudUser?> = _user

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (token.isNullOrBlank()) {
            _user.value = null
            return
        }
        _user.value = CloudUser(
            id = prefs.getLong("id", 0L),
            email = prefs.getString("email", "").orEmpty(),
            phone = prefs.getString("phone", "").orEmpty(),
            name = prefs.getString("name", "").orEmpty(),
        )
    }

    fun token(context: Context): String? {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("token", null)
    }

    fun save(context: Context, token: String, user: CloudUser) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("token", token)
            .putLong("id", user.id)
            .putString("email", user.email)
            .putString("phone", user.phone)
            .putString("name", user.name)
            .apply()
        _user.value = user
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        _user.value = null
    }
}

object CloudApi {
    private const val BASE = BuildConfig.API_BASE

    suspend fun register(context: Context, account: String, password: String, name: String, phone: Boolean) {
        val path = if (phone) "/auth/phone/register" else "/auth/email/register"
        val body = JSONObject().put("account", account.trim()).put("password", password).put("name", name.trim())
        keep(context, request("POST", path, body, null))
    }

    suspend fun login(context: Context, account: String, password: String) {
        val body = JSONObject().put("account", account.trim()).put("password", password)
        keep(context, request("POST", "/auth/login", body, null))
    }

    suspend fun updateProfile(context: Context, name: String, email: String, phone: String, password: String) {
        val body = JSONObject().put("name", name.trim())
        body.put("email", email.trim())
        body.put("phone", phone.trim())
        if (password.isNotBlank()) body.put("password", password)
        val json = request("PATCH", "/auth/me", body, Session.token(context))
        val user = readUser(json)
        val token = Session.token(context).orEmpty()
        Session.save(context, token, user)
    }

    suspend fun push(context: Context, store: RitualStore) {
        val token = Session.token(context) ?: return
        val state = store.state.value
        val today = LocalDate.now()
        val body = JSONObject()
            .put("ritual", store.exportJson())
            .put("week_score", spanScore(state, weekDates(state.mondayWeek), today).average)
            .put("month_score", spanScore(state, monthDates(today), today).average)
        request("PUT", "/sync", body, token)
    }

    suspend fun pull(context: Context): String? {
        val token = Session.token(context) ?: return null
        val json = request("GET", "/sync", null, token)
        val ritual = json.opt("ritual")
        return if (ritual == null || ritual == JSONObject.NULL || json.isNull("ritual")) null else json.optString("ritual")
    }

    suspend fun understandMeal(context: Context, jpeg: ByteArray): MealReading? {
        val token = Session.token(context) ?: return null
        val body = JSONObject()
            .put("image_base64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            .put("media_type", "image/jpeg")
        val json = request("POST", "/ai/meal", body, token, readMs = 90000)
        if (!json.optBoolean("ready")) {
            val message = json.optString("message")
            if (message.isNotBlank()) throw CloudError(message)
            return null
        }
        val foods = buildList {
            val arr = json.optJSONArray("foods") ?: return@buildList
            for (i in 0 until arr.length()) {
                val name = arr.optString(i).trim()
                if (name.isNotEmpty()) add(name)
            }
        }
        return MealReading(
            score = json.optInt("score"),
            title = json.optString("title").ifBlank { "这一餐" },
            commentary = json.optString("commentary"),
            produce = json.optInt("produce"),
            variety = json.optInt("variety"),
            fresh = json.optInt("fresh"),
            balance = json.optInt("balance"),
            foods = foods,
            usedModel = true,
            kcal = json.optInt("kcal"),
        )
    }

    suspend fun summarize(context: Context, span: String, brief: String): Digest {
        val body = JSONObject().put("span", span).put("brief", brief)
        val json = request("POST", "/ai/summary", body, Session.token(context), readMs = 90000)
        if (!json.optBoolean("ready")) {
            val message = json.optString("message")
            throw CloudError(if (message.isBlank()) "总结没有写成" else message)
        }
        return Digest(
            span = span,
            day = todayKey(),
            title = json.optString("title").ifBlank { "这一程" },
            verdict = json.optString("verdict"),
            keep = json.optString("keep"),
            leave = json.optString("leave"),
        )
    }

    suspend fun rank(context: Context, month: Boolean): List<RankRow> {
        val path = if (month) "/rank?span=month" else "/rank?span=week"
        val json = request("GET", path, null, Session.token(context))
        val arr = json.optJSONArray("board") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    RankRow(
                        place = item.optInt("place"),
                        name = item.optString("name"),
                        score = item.optInt("score"),
                        mine = item.optBoolean("mine"),
                    ),
                )
            }
        }
    }

    private fun keep(context: Context, json: JSONObject) {
        Session.save(context, json.getString("token"), readUser(json.getJSONObject("user")))
    }

    private fun readUser(json: JSONObject): CloudUser {
        return CloudUser(
            id = json.optLong("id"),
            email = json.optString("email"),
            phone = json.optString("phone"),
            name = json.optString("name"),
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject?,
        token: String?,
        readMs: Int = 8000,
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = readMs
                setRequestProperty("Accept", "application/json")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            val json = if (text.trim().startsWith("{")) JSONObject(text) else JSONObject()
            if (code !in 200..299) {
                val detail = json.opt("detail")
                val message = when (detail) {
                    is String -> detail
                    else -> "云端没有连上"
                }
                throw CloudError(message)
            }
            json
        }
    }
}
