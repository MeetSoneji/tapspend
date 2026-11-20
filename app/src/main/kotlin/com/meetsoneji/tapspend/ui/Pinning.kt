package com.meetsoneji.tapspend.ui

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.tile.QuickLogTileService
import com.meetsoneji.tapspend.widget.QuickLogWidgetReceiver

/** Asks the launcher to pin the widget. Returns false when the launcher does not support pinning. */
fun requestPinWidget(context: Context): Boolean {
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) return false
    return mgr.requestPinAppWidget(ComponentName(context, QuickLogWidgetReceiver::class.java), null, null)
}

/** Android 13+ can show a one-tap "add tile" prompt. Returns false on older versions (user adds it by hand). */
fun requestAddTile(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return false
    sbm.requestAddTileService(
        ComponentName(context, QuickLogTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_tile),
        ContextCompat.getMainExecutor(context),
    ) { }
    return true
}
