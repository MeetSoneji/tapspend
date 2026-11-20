package com.meetsoneji.tapspend.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.meetsoneji.tapspend.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Debounced (500 ms) widget refresh, called only when data or settings actually change. */
class WidgetUpdater(private val context: Context, private val scope: CoroutineScope) {
    private var job: Job? = null

    @Synchronized
    fun requestUpdate() {
        job?.cancel()
        job = scope.launch {
            delay(500)
            runCatching { QuickLogWidget().updateAll(context) }
        }
    }
}

/**
 * One non-wakeup RTC alarm per day so "Aaj" resets at local midnight. [AlarmManager.RTC] does not wake the
 * device, needs no exact-alarm permission and costs nothing while asleep.
 */
object MidnightScheduler {
    const val ACTION_MIDNIGHT = "com.meetsoneji.tapspend.ACTION_MIDNIGHT"

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000
        am.set(AlarmManager.RTC, next, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, MidnightReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Handles our midnight alarm plus BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED and LOCALE_CHANGED. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MidnightScheduler.schedule(context)
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                QuickLogWidget().updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
