package com.meetsoneji.tapspend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 12 named colour keys with light/dark tones. Categories store the key, never a hex, so themes can remap. */
object CategoryPalette {
    private val tones: LinkedHashMap<String, Pair<Color, Color>> = linkedMapOf(
        "teal" to (Color(0xFF2F7F78) to Color(0xFF6CC3BA)),
        "amber" to (Color(0xFFA9711C) to Color(0xFFE2B163)),
        "rose" to (Color(0xFFB4466A) to Color(0xFFE48AA7)),
        "indigo" to (Color(0xFF4F5BB0) to Color(0xFF9AA4E6)),
        "green" to (Color(0xFF3E8E5A) to Color(0xFF7CC898)),
        "orange" to (Color(0xFFC0652B) to Color(0xFFEDA06E)),
        "purple" to (Color(0xFF7B55B0) to Color(0xFFB79BE0)),
        "blue" to (Color(0xFF2F6FB3) to Color(0xFF7FB0E6)),
        "pink" to (Color(0xFFB44F8A) to Color(0xFFE493C1)),
        "lime" to (Color(0xFF6E8B2A) to Color(0xFFB4CC73)),
        "cyan" to (Color(0xFF2A88A0) to Color(0xFF74C4D8)),
        "slate" to (Color(0xFF677385) to Color(0xFFA9B3C1)),
    )

    val keys: List<String> get() = tones.keys.toList()

    fun color(key: String, dark: Boolean): Color {
        val pair = tones[key] ?: tones.getValue("slate")
        return if (dark) pair.second else pair.first
    }

    @Composable
    fun color(key: String): Color = color(key, LocalDarkTheme.current)
}
