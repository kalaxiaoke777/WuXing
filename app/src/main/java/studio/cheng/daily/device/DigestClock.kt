package studio.cheng.daily.device

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.digestBrief
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object DigestClock {
    fun arm(context: Context) {
        val app = context.applicationContext
        schedule(app, "day", next(22, 0) { it.plusDays(1) })
        schedule(app, "week", nextWeek())
        schedule(app, "month", nextMonth())
    }

    fun run(context: Context, span: String) {
        val app = context.applicationContext
        val store = RitualStore(app)
        val brief = digestBrief(store.state.value, span)
        if (Session.token(app).isNullOrBlank()) {
            notify(app, "登录之后，才会把这一程交给模型。")
        } else {
            try {
                val digest = kotlinx.coroutines.runBlocking { CloudApi.summarize(app, span, brief) }
                store.saveDigest(digest)
                try {
                    kotlinx.coroutines.runBlocking { CloudApi.push(app, store) }
                } catch (_: Throwable) {
                }
                val line = listOf(digest.verdict, digest.keep, digest.leave).filter { it.isNotBlank() }.joinToString(" ")
                notify(app, if (line.isBlank()) digest.title else "${digest.title}  $line")
            } catch (error: CloudError) {
                notify(app, error.message ?: "总结没有写成")
            } catch (_: Throwable) {
                notify(app, "总结没有写成")
            }
        }
        arm(app)
    }

    private fun schedule(context: Context, span: String, whenMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, DigestReceiver::class.java).putExtra("span", span)
        val pending = PendingIntent.getBroadcast(
            context,
            span.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }
    }

    private fun next(hour: Int, minute: Int, roll: (LocalDate) -> LocalDate): Long {
        val zone = ZoneId.systemDefault()
        var day = LocalDate.now()
        var moment = day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        if (moment <= System.currentTimeMillis()) {
            day = roll(day)
            moment = day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        }
        return moment
    }

    private fun nextWeek(): Long {
        val zone = ZoneId.systemDefault()
        var day = LocalDate.now().with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        var moment = day.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        if (moment <= System.currentTimeMillis()) {
            day = day.plusWeeks(1)
            moment = day.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return moment
    }

    private fun nextMonth(): Long {
        val zone = ZoneId.systemDefault()
        var day = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth())
        var moment = day.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        if (moment <= System.currentTimeMillis()) {
            day = LocalDate.now().plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
            moment = day.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return moment
    }

    private fun notify(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("digest", "日课总结", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notice = NotificationCompat.Builder(context, "digest")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("五行")
            .setContentText(text.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(text.hashCode(), notice)
        } catch (_: SecurityException) {
        }
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DigestClock.arm(context)
            return
        }
        val span = intent.getStringExtra("span") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DigestClock.run(context, span)
            } finally {
                pending.finish()
            }
        }
    }
}
