package studio.cheng.daily.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.device.CloudApi
import studio.cheng.daily.device.CloudError
import studio.cheng.daily.device.Session

@Composable
fun SignGate(store: RitualStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneMode by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: CloudError) {
                hint = error.message ?: "云端没有连上"
            } catch (_: Throwable) {
                hint = "云端没有连上。先在 server 目录把后端跑起来。"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        InkText("个人", size = 12.sp, color = Ink.muted, spacing = 3.sp)
        InkText("登录", modifier = Modifier.padding(top = 4.dp), size = 34.sp, display = true)
        InkText(
            "登录之后才能改身体、一周和云端。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
        )
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("邮箱", "用邮箱", { phoneMode = false }, accent = !phoneMode)
            Seal("手机", "用手机", { phoneMode = true }, accent = phoneMode)
        }
        CloudField(if (phoneMode) "手机号" else "邮箱", account, { account = it })
        CloudField("密码", password, { password = it })
        CloudField("称呼", name, { name = it.take(12) })
        Seal(
            "登录",
            "登录账号",
            {
                run {
                    CloudApi.login(context, account, password)
                    land(context, store)
                    hint = "已登录"
                }
            },
            Modifier.fillMaxWidth().padding(top = 18.dp),
            accent = true,
        )
        Seal(
            "注册",
            "注册账号",
            {
                run {
                    CloudApi.register(context, account, password, name, phoneMode)
                    if (name.isNotBlank()) store.setName(name)
                    land(context, store)
                    hint = "已注册"
                }
            },
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (hint.isNotBlank()) {
            InkText(hint, modifier = Modifier.padding(top = 14.dp), size = 13.sp, color = Ink.bronze, line = 20.sp)
        }
    }
}

@Composable
fun ProfileEditor(store: RitualStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by Session.user.collectAsState()
    val signed = user ?: return
    var email by remember(signed.id) { mutableStateOf(signed.email) }
    var phone by remember(signed.id) { mutableStateOf(signed.phone) }
    var password by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: CloudError) {
                hint = error.message ?: "云端没有连上"
            } catch (_: Throwable) {
                hint = "云端没有连上"
            }
        }
    }

    InkText("账号", modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
    CloudField("邮箱", email, { email = it })
    CloudField("手机", phone, { phone = it })
    CloudField("新密码", password, { password = it })
    InkText("密码留空就是不改。", modifier = Modifier.padding(top = 6.dp), size = 12.sp, color = Ink.faint)
    Seal(
        "保存资料",
        "保存云端资料",
        {
            run {
                CloudApi.updateProfile(context, store.state.value.name, email, phone, password)
                password = ""
                hint = "资料已改"
            }
        },
        Modifier.padding(top = 12.dp),
        accent = true,
    )
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Seal("上传", "把本机记录上传到云端", { run { CloudApi.push(context, store); hint = "已上传" } })
        Seal("导入", "从云端导入记录", {
            run {
                val raw = CloudApi.pull(context)
                if (raw.isNullOrBlank()) hint = "云端还没有记录"
                else {
                    store.replaceFromCloud(raw)
                    hint = "已从云端导入"
                }
            }
        })
        Seal("退出", "退出账号", { Session.clear(context) })
    }
    if (hint.isNotBlank()) {
        InkText(hint, modifier = Modifier.padding(top = 10.dp), size = 13.sp, color = Ink.bronze)
    }
}

private suspend fun land(context: android.content.Context, store: RitualStore) {
    val raw = CloudApi.pull(context)
    if (raw.isNullOrBlank()) CloudApi.push(context, store) else store.replaceFromCloud(raw)
}

@Composable
private fun CloudField(label: String, value: String, onChange: (String) -> Unit) {
    InkText(label, modifier = Modifier.padding(top = 14.dp), size = 12.sp, color = Ink.muted, spacing = 2.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        textStyle = TextStyle(color = Ink.text, fontSize = 16.sp, fontFamily = FontFamily.SansSerif),
        cursorBrush = SolidColor(Ink.bronze),
        singleLine = true,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Ink.line)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) InkText(label, size = 14.sp, color = Ink.faint)
                inner()
            }
        },
    )
}

@Composable
fun RankPane(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<studio.cheng.daily.device.RankRow>>(emptyList()) }
    var hint by remember { mutableStateOf("按云端记下的评分排。欲望那些细节不会公开。") }

    fun load(useMonth: Boolean) {
        scope.launch {
            try {
                rows = CloudApi.rank(context, useMonth)
                if (rows.isEmpty()) hint = "还没有人把记录传到云端。"
            } catch (error: CloudError) {
                hint = error.message ?: "排名没有读出来"
            } catch (_: Throwable) {
                hint = "云端没有连上"
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(month) { load(month) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Seal("收回", "回到个人", onBack)
        PaneHeader("排名", if (month) "本月" else "本周", "")
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("本周", "看本周排名", { month = false }, accent = !month)
            Seal("本月", "看本月排名", { month = true }, accent = month)
        }
        InkText(hint, modifier = Modifier.padding(top = 10.dp), size = 13.sp, color = Ink.faint, line = 20.sp)
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkText("${row.place}", size = 18.sp, color = if (row.mine) Ink.bronze else Ink.muted, display = true)
                InkText(
                    row.name + if (row.mine) "  ·  我" else "",
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                    size = 16.sp,
                )
                InkText("${row.score}", size = 18.sp, color = Ink.bronze, display = true)
            }
        }
        Box(Modifier.padding(bottom = 18.dp))
    }
}
