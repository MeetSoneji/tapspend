package com.meetsoneji.tapspend

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.AppRoot
import kotlinx.coroutines.launch

/** The only exported activity (launcher + shortcut action). Everything else is internal. */
class MainActivity : ComponentActivity() {
    private var pendingEditId by mutableStateOf<String?>(null)
    private var pendingReviewId by mutableStateOf<String?>(null)
    private var settingsNow by mutableStateOf<UserSettings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Shortcut / Pixel Quick Tap: forward immediately to the lightweight Quick Log sheet and finish.
        if (intent?.action == ACTION_QUICK_LOG) {
            startActivity(Intent(this, QuickLogActivity::class.java).putExtra(QuickLogActivity.EXTRA_SOURCE, QuickLogActivity.SOURCE_SHORTCUT))
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        lifecycleScope.launch {
            applicationContext.container.settings.flow.collect { s ->
                settingsNow = s
                if (s.secureScreen) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            AppRoot(
                settings = settingsNow, pendingEditId = pendingEditId, onEditHandled = { pendingEditId = null },
                pendingReviewId = pendingReviewId, onReviewHandled = { pendingReviewId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_QUICK_LOG) {
            startActivity(Intent(this, QuickLogActivity::class.java).putExtra(QuickLogActivity.EXTRA_SOURCE, QuickLogActivity.SOURCE_SHORTCUT))
            return
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_EDIT -> pendingEditId = intent.getStringExtra(EXTRA_EXPENSE_ID)?.takeIf { it.length in 1..64 }
            ACTION_REVIEW -> pendingReviewId = intent.getStringExtra(EXTRA_EXPENSE_ID)?.takeIf { it.length in 1..64 } ?: ""
        }
    }

    companion object {
        const val ACTION_QUICK_LOG = "com.meetsoneji.tapspend.ACTION_QUICK_LOG"
        const val ACTION_EDIT = "com.meetsoneji.tapspend.ACTION_EDIT"
        const val ACTION_REVIEW = "com.meetsoneji.tapspend.ACTION_REVIEW"
        const val EXTRA_EXPENSE_ID = "expense_id"
    }
}
