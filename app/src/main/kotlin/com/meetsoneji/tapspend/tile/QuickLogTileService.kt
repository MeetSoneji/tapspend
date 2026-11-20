package com.meetsoneji.tapspend.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.meetsoneji.tapspend.QuickLogActivity

/** Passive tile (ACTIVE_TILE = false in the manifest): the system binds it only while the shade is visible. */
@android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
class QuickLogTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val launch = {
            val intent = Intent(this, QuickLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(QuickLogActivity.EXTRA_SOURCE, QuickLogActivity.SOURCE_TILE)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                // The PendingIntent overload only exists on API 34+; this is the supported path below it.
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(launch) else launch()
    }
}
