package com.meetsoneji.tapspend.ui

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class HapticKind { KEY, TICK, CONFIRM, REJECT }

/** Uses View.performHapticFeedback, so no VIBRATE permission is needed. */
fun View.haptic(kind: HapticKind, enabled: Boolean) {
    if (!enabled) return
    val constant = when (kind) {
        HapticKind.KEY -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticKind.TICK -> HapticFeedbackConstants.CLOCK_TICK
        HapticKind.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        HapticKind.REJECT -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else return
    }
    performHapticFeedback(constant)
}

/** Motion is disabled entirely when the system animator scale is 0 (reduced motion). */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
