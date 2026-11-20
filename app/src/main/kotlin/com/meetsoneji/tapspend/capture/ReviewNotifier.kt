package com.meetsoneji.tapspend.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meetsoneji.tapspend.MainActivity
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.container
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.core.capture.TxnKind
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.ui.categoryName

object ReviewNotifier {
    const val CHANNEL_REVIEW = "review"
    const val CHANNEL_REMINDER = "reminder"
    const val ACTION_APPROVE = "com.meetsoneji.tapspend.ACTION_APPROVE"
    const val ACTION_NOT_SPENDING = "com.meetsoneji.tapspend.ACTION_NOT_SPENDING"
    const val EXTRA_ID = "expense_id"
    private const val REMINDER_ID = 1

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, id: String?): PendingIntent =
        PendingIntent.getActivity(
            context, id?.hashCode() ?: 0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_REVIEW).putExtra(EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(context: Context, action: String, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action + id).hashCode(),
            Intent(context, ReviewActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @android.annotation.SuppressLint("MissingPermission") // allowed() checks POST_NOTIFICATIONS first
    suspend fun notifyNew(context: Context, e: ExpenseEntity, reason: String) {
        if (!allowed(context)) return
        ensureChannels(context)
        val c = context.container
        val category = c.database.categoryDao().get(e.categoryId)?.let { categoryName(context, it) }
        val account = e.accountId?.let { c.database.accountDao().get(it)?.label }
        val amount = Money.format(e.amountMinor, e.currency)
        val who = e.merchant ?: context.getString(R.string.unknown_payee)
        val title = when (e.kind) {
            TxnKind.REFUND -> context.getString(R.string.notif_title_refund, amount, who)
            TxnKind.CASHBACK -> context.getString(R.string.notif_title_cashback, amount)
            TxnKind.TRANSFER -> context.getString(R.string.notif_title_transfer, amount, who)
            else -> context.getString(R.string.notif_title_spend, amount, who)
        }
        val guess = when (e.kind) {
            TxnKind.TRANSFER -> context.getString(R.string.notif_guess_transfer, reason)
            TxnKind.CASHBACK -> context.getString(R.string.notif_guess_cashback)
            else -> category?.let { context.getString(R.string.notif_guess_category, it) } ?: ""
        }
        val body = listOfNotNull(account, guess.takeIf { it.isNotEmpty() }).joinToString("\n")
        val b = NotificationCompat.Builder(context, CHANNEL_REVIEW)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(body.replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context, e.id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        when (e.kind) {
            TxnKind.EXPENSE -> {
                b.addAction(0, context.getString(R.string.notif_action_approve_as, category ?: context.getString(R.string.cat_other)), action(context, ACTION_APPROVE, e.id))
                b.addAction(0, context.getString(R.string.notif_action_change), openApp(context, e.id))
                b.addAction(0, context.getString(R.string.notif_action_not_spending), action(context, ACTION_NOT_SPENDING, e.id))
            }
            TxnKind.TRANSFER -> {
                b.addAction(0, context.getString(R.string.notif_action_confirm_transfer), action(context, ACTION_APPROVE, e.id))
                b.addAction(0, context.getString(R.string.notif_action_its_spending), openApp(context, e.id))
            }
            else -> {
                b.addAction(0, context.getString(R.string.notif_action_confirm), action(context, ACTION_APPROVE, e.id))
                b.addAction(0, context.getString(R.string.notif_action_change), openApp(context, e.id))
            }
        }
        runCatching { NotificationManagerCompat.from(context).notify(e.id.hashCode(), b.build()) }
    }

    @android.annotation.SuppressLint("MissingPermission") // allowed() checks POST_NOTIFICATIONS first
    fun notifyReminder(context: Context, count: Int) {
        if (!allowed(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.resources.getQuantityString(R.plurals.reminder_title, count, count))
            .setContentText(context.getString(R.string.reminder_body))
            .setContentIntent(openApp(context, null))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(REMINDER_ID, n) }
    }

    fun cancel(context: Context, id: String) = NotificationManagerCompat.from(context).cancel(id.hashCode())
}
