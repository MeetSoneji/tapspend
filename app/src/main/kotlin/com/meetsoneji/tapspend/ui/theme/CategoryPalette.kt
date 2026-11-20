package com.meetsoneji.tapspend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 12 named colour keys with light/dark tones. Categories store the key, never a hex, so themes can remap. */
object CategoryPalette {
    private val tones: LinkedHashMap<String, Pair<Color, Color>> = linkedMapOf(
        "teal" to (Color(0xFF14B8A6) to Color(0xFF2DD4BF)),
        "amber" to (Color(0xFFF59E0B) to Color(0xFFFBBF24)),
        "rose" to (Color(0xFFF43F5E) to Color(0xFFFB7185)),
        "indigo" to (Color(0xFF6366F1) to Color(0xFFA5B4FC)),
        "green" to (Color(0xFF22C55E) to Color(0xFF4ADE80)),
        "orange" to (Color(0xFFF97316) to Color(0xFFFB923C)),
        "purple" to (Color(0xFFA855F7) to Color(0xFFC084FC)),
        "blue" to (Color(0xFF3B82F6) to Color(0xFF60A5FA)),
        "pink" to (Color(0xFFEC4899) to Color(0xFFF472B6)),
        "lime" to (Color(0xFF84CC16) to Color(0xFFBEF264)),
        "cyan" to (Color(0xFF06B6D4) to Color(0xFF22D3EE)),
        "slate" to (Color(0xFF64748B) to Color(0xFF94A3B8)),
    )

    val keys: List<String> get() = tones.keys.toList()

    fun color(key: String, dark: Boolean): Color {
        val pair = tones[key] ?: tones.getValue("slate")
        return if (dark) pair.second else pair.first
    }

    @Composable
    fun color(key: String): Color = color(key, LocalDarkTheme.current)
}
