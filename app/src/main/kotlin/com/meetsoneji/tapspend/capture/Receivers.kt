package com.meetsoneji.tapspend.capture

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meetsoneji.tapspend.container
import com.meetsoneji.tapspend.core.capture.TxnParser
import com.meetsoneji.tapspend.data.TxnSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Bank alerts arrive here. Only DLT bank sender ids are parsed; personal messages are never read. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val bySender = parts.groupBy { it.originatingAddress.orEmpty() }
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                bySender.forEach { (sender, msgs) ->
                    if (!TxnParser.isBankSender(sender)) return@forEach
                    val body = msgs.joinToString("") { it.messageBody.orEmpty() }
                    val parsed = TxnParser.parse(body, sender) ?: return@forEach
                    val at = Instant.ofEpochMilli(msgs.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
                    context.container.capture.ingest(parsed, TxnSource.SMS, at, notify = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** One-time import of the last 90 days of bank alerts, so you start with history instead of an empty app. */
object HistoryImporter {
    suspend fun import(context: Context, days: Long = 90): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return 0
        val since = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000
        var added = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { cur ->
            while (cur.moveToNext()) {
                val sender = cur.getString(0) ?: continue
                if (!TxnParser.isBankSender(sender)) continue
                val parsed = TxnParser.parse(cur.getString(1) ?: continue, sender) ?: continue
                if (context.container.capture.ingest(parsed, TxnSource.SMS, Instant.ofEpochMilli(cur.getLong(2)), notify = false) != null) added++
            }
        }
        return added
    }
}

/**
 * Reads payment confirmations from UPI and bank apps (for payments with no SMS). Messaging apps are deliberately
 * excluded so bank SMS shown as notifications aren't counted twice.
 */
class PaymentNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PAYMENT_APPS || sbn.isOngoing) return
        val extras = sbn.notification.extras
        val text = listOfNotNull(
            extras.getCharSequence(NotificationCompat.EXTRA_TITLE), extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(NotificationCompat.EXTRA_TEXT),
        ).joinToString(". ")
        val parsed = TxnParser.parse(text, sbn.packageName) ?: return
        val c = applicationContext.container
        c.appScope.launch { c.capture.ingest(parsed, TxnSource.NOTIFICATION, Instant.ofEpochMilli(sbn.postTime), notify = true) }
    }

    companion object {
        val PAYMENT_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping", "com.dreamplug.androidapp", "money.super.payments", "com.mobikwik_new",
            "com.snapwork.hdfc", "com.hdfcbank.payzapp", "com.csam.icici.bank.imobile", "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus",
            "com.axis.mobile", "com.msf.kbank.mobile", "com.idfcfirstbank.optimus", "com.fss.indus", "com.onecard.android",
        )
    }
}

/** Handles the buttons on a review notification without opening the app. */
class ReviewActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReviewNotifier.EXTRA_ID)?.takeIf { it.length in 1..64 } ?: return
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                when (intent.action) {
                    ReviewNotifier.ACTION_APPROVE -> c.repository.approve(id)
                    ReviewNotifier.ACTION_NOT_SPENDING -> c.repository.approve(id, kind = "TRANSFER")
                }
                ReviewNotifier.cancel(context, id)
            } finally {
                pending.finish()
            }
        }
    }
}

/** A single inexact daily alarm at your chosen time; posts one reminder only if something is waiting. */
object ReminderScheduler {
    const val ACTION = "com.meetsoneji.tapspend.ACTION_REVIEW_REMINDER"

    fun schedule(context: Context, enabled: Boolean, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (!enabled) return
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(minutes / 60, minutes % 60)
        var next = LocalDate.now().atTime(time).atZone(zone)
        if (!next.toInstant().isAfter(Instant.now())) next = next.plusDays(1)
        am.set(AlarmManager.RTC, next.toInstant().toEpochMilli(), pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                val s = c.settings.flow.first()
                ReminderScheduler.schedule(context, s.reminderEnabled, s.reminderMinutes)
                if (intent.action == ReminderScheduler.ACTION && s.reminderEnabled) {
                    val n = c.database.expenseDao().pendingCount()
                    if (n > 0) ReviewNotifier.notifyReminder(context, n)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
