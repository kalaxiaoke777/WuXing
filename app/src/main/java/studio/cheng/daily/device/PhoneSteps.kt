package studio.cheng.daily.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.suspendCancellableCoroutine
import studio.cheng.daily.data.todayKey
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

sealed class StepRead {
    data class Ready(val steps: Int) : StepRead()
    data object NeedPermission : StepRead()
    data object NeedSensor : StepRead()
    data object NeedProvider : StepRead()
    data class Failed(val reason: String) : StepRead()
}

object PhoneSteps {
    val healthPermission: String = HealthPermission.getReadPermission(StepsRecord::class)

    suspend fun read(context: Context): StepRead {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (healthPermission !in granted) return StepRead.NeedPermission
            return try {
                StepRead.Ready(healthSteps(client))
            } catch (_: Throwable) {
                StepRead.Failed("健康数据没有读出来")
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 29 && !sensorAllowed(context)) {
            if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                return StepRead.NeedProvider
            }
            return StepRead.NeedSensor
        }
        val sensor = sensorSteps(context)
        if (sensor != null) return StepRead.Ready(sensor)
        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) return StepRead.NeedProvider
        return StepRead.Failed("这台手机没有计步")
    }

    fun providerIntent(): Intent {
        return Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    }

    private suspend fun healthSteps(client: HealthConnectClient): Int {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant()
        val end = java.time.Instant.now()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return response.records.sumOf { it.count }.toInt().coerceAtLeast(0)
    }

    private fun sensorAllowed(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun sensorSteps(context: Context): Int? {
        if (android.os.Build.VERSION.SDK_INT >= 29 && !sensorAllowed(context)) return null
        val manager = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        val total = suspendCancellableCoroutine<Long?> { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    manager.unregisterListener(this)
                    if (cont.isActive) cont.resume(event.values.firstOrNull()?.toLong())
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            val registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            if (!registered && cont.isActive) cont.resume(null)
            cont.invokeOnCancellation { manager.unregisterListener(listener) }
        } ?: return null
        return stepsSinceMorning(context, total)
    }

    private fun stepsSinceMorning(context: Context, total: Long): Int {
        val prefs = context.getSharedPreferences("cheng.steps", Context.MODE_PRIVATE)
        val today = todayKey()
        val savedDay = prefs.getString("day", null)
        val savedTotal = prefs.getLong("total", -1L)
        val savedSteps = prefs.getInt("steps", 0)
        val zone = java.time.ZoneId.systemDefault()
        val bootedToday = java.time.Instant.ofEpochMilli(
            System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime(),
        ).atZone(zone).toLocalDate() == java.time.LocalDate.now()
        val steps = when {
            savedDay != today || savedTotal < 0L -> if (bootedToday) total.toInt() else 0
            total >= savedTotal -> savedSteps + (total - savedTotal).toInt()
            bootedToday -> total.toInt()
            else -> savedSteps + total.toInt()
        }
        prefs.edit().putString("day", today).putLong("total", total).putInt("steps", steps).apply()
        return steps.coerceAtLeast(0)
    }
}
