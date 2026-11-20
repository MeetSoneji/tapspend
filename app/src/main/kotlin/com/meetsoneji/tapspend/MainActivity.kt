package com.meetsoneji.tapspend

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.AppRoot
import kotlinx.coroutines.launch

/** The only exported activity (launcher + shortcut action). Everything else is internal. */
class MainActivity : FragmentActivity() {
    private var pendingEditId by mutableStateOf<String?>(null)
    private var locked by mutableStateOf(true)
    private var settingsNow by mutableStateOf<UserSettings?>(null)
    private var backgroundedAt = 0L
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Shortcut / Pixel Quick Tap: forward immediately to the lightweight Quick Log sheet and finish.
        if (intent?.action == ACTION_QUICK_LOG) {
            startActivity(
                Intent(this, QuickLogActivity::class.java)
                    .putExtra(QuickLogActivity.EXTRA_SOURCE, QuickLogActivity.SOURCE_SHORTCUT),
            )
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        // Re-lock after 30 s in the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { backgroundedAt = SystemClock.elapsedRealtime() }
            override fun onStart(owner: LifecycleOwner) {
                if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > LOCK_GRACE_MS) locked = true
            }
        })

        lifecycleScope.launch {
            applicationContext.container.settings.flow.collect { s ->
                settingsNow = s
                applyWindowPrivacy(s)
            }
        }

        setContent {
            AppRoot(
                settings = settingsNow,
                locked = locked,
                onRequestUnlock = ::authenticate,
                pendingEditId = pendingEditId,
                onEditHandled = { pendingEditId = null },
                canUseAppLock = ::canAuthenticate,
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
        if (intent?.action == ACTION_EDIT) {
            pendingEditId = intent.getStringExtra(EXTRA_EXPENSE_ID)?.takeIf { it.length in 1..64 }
        }
    }

    private fun applyWindowPrivacy(s: UserSettings) {
        if (s.secureScreen) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // With app lock on, the Recents card must not show a screenshot of the app.
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!s.appLockEnabled)
        if (!s.appLockEnabled) locked = false
    }

    fun canAuthenticate(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        }

    private val credentialLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        promptShowing = false
        if (it.resultCode == RESULT_OK) locked = false
    }

    /** BiometricPrompt (strong biometric or device credential). API < 30 falls back to the system credential screen. */
    private fun authenticate() {
        if (promptShowing) return
        promptShowing = true
        val title = getString(R.string.lock_prompt_title)
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt(
                this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        promptShowing = false
                        locked = false
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { promptShowing = false }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder().setTitle(title)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build(),
            )
        } else {
            @Suppress("DEPRECATION")
            val intent = getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) { promptShowing = false; locked = false } else credentialLauncher.launch(intent)
        }
    }

    companion object {
        const val ACTION_QUICK_LOG = "com.meetsoneji.tapspend.ACTION_QUICK_LOG"
        const val ACTION_EDIT = "com.meetsoneji.tapspend.ACTION_EDIT"
        const val EXTRA_EXPENSE_ID = "expense_id"
        private const val LOCK_GRACE_MS = 30_000L
    }
}
