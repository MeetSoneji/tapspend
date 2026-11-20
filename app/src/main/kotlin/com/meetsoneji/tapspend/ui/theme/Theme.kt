package com.meetsoneji.tapspend.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.data.ThemeMode

/*
 * "Review inbox": a bank slip on a grey desk. Paper, ink and pencil for everything; ledger green only for
 * "confirmed / confirm"; due amber only for "waiting for you". No gradients, no glow, no decoration.
 */
@Immutable
data class Ledger(val green: Color, val due: Color, val slip: Color, val rule: Color, val credit: Color)

private val LightLedger = Ledger(green = Color(0xFF0B6E4F), due = Color(0xFFA35A0A), slip = Color.White, rule = Color(0xFFD5DAE1), credit = Color(0xFF0B6E4F))
private val DarkLedger = Ledger(green = Color(0xFF4CB98A), due = Color(0xFFE3A15A), slip = Color(0xFF1E2631), rule = Color(0xFF2E3846), credit = Color(0xFF4CB98A))

val LocalLedger = staticCompositionLocalOf { LightLedger }

val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EDE3), onPrimaryContainer = Color(0xFF03301F),
    secondary = Color(0xFF3F4A58), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E7EC), onSecondaryContainer = Color(0xFF1A2330),
    tertiary = Color(0xFFA35A0A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E6D2), onTertiaryContainer = Color(0xFF3A1E00),
    background = Color(0xFFF2F4F7), onBackground = Color(0xFF1A2330),
    surface = Color(0xFFF2F4F7), onSurface = Color(0xFF1A2330),
    surfaceVariant = Color(0xFFE6E9EE), onSurfaceVariant = Color(0xFF5D6876),
    outline = Color(0xFF8A94A2), outlineVariant = Color(0xFFD5DAE1),
    error = Color(0xFFB42318), onError = Color.White,
    errorContainer = Color(0xFFFDE3E0), onErrorContainer = Color(0xFF4A0A05),
    surfaceBright = Color.White, surfaceDim = Color(0xFFE4E8ED),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEDF0F3),
    surfaceContainerHighest = Color(0xFFE6E9EE),
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF4CB98A), onPrimary = Color(0xFF03301F),
    primaryContainer = Color(0xFF14412F), onPrimaryContainer = Color(0xFFCFEDE0),
    secondary = Color(0xFFB4BDC9), onSecondary = Color(0xFF1A2330),
    secondaryContainer = Color(0xFF2A3441), onSecondaryContainer = Color(0xFFE8ECF1),
    tertiary = Color(0xFFE3A15A), onTertiary = Color(0xFF3A1E00),
    tertiaryContainer = Color(0xFF4A2E0E), onTertiaryContainer = Color(0xFFF7E6D2),
    background = Color(0xFF151B24), onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF151B24), onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF232C38), onSurfaceVariant = Color(0xFFA3ADBA),
    outline = Color(0xFF6B7684), outlineVariant = Color(0xFF2E3846),
    error = Color(0xFFF08A7E), onError = Color(0xFF4A0A05),
    errorContainer = Color(0xFF5C1A12), onErrorContainer = Color(0xFFFDE3E0),
    surfaceBright = Color(0xFF2A3441), surfaceDim = Color(0xFF151B24),
    surfaceContainerLowest = Color(0xFF10151C), surfaceContainerLow = Color(0xFF1A212B),
    surfaceContainer = Color(0xFF1E2631), surfaceContainerHigh = Color(0xFF242D39),
    surfaceContainerHighest = Color(0xFF2A3441),
)

/** Mukta (Ek Type, Mumbai): Indian-made, native ₹, real tabular figures. One family; hierarchy from weight and size. */
val Mukta = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_semibold, FontWeight.SemiBold),
)

/** Tabular figures so digits line up in columns and don't jump while typing. */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

private fun t(size: Int, line: Int, weight: FontWeight) = TextStyle(fontFamily = Mukta, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

// Scale 13 / 15 / 17 / 20 / 26 / 34 / 44 (a ~1.15–1.3 progression).
private val AppTypography = Typography(
    displayLarge = t(44, 50, FontWeight.SemiBold), displayMedium = t(44, 50, FontWeight.SemiBold), displaySmall = t(34, 40, FontWeight.SemiBold),
    headlineLarge = t(34, 40, FontWeight.Medium), headlineMedium = t(26, 32, FontWeight.Medium), headlineSmall = t(20, 26, FontWeight.Medium),
    titleLarge = t(20, 26, FontWeight.SemiBold), titleMedium = t(17, 23, FontWeight.SemiBold), titleSmall = t(15, 20, FontWeight.SemiBold),
    bodyLarge = t(17, 24, FontWeight.Normal), bodyMedium = t(15, 21, FontWeight.Normal), bodySmall = t(13, 18, FontWeight.Normal),
    labelLarge = t(15, 20, FontWeight.Medium), labelMedium = t(13, 18, FontWeight.Medium), labelSmall = t(13, 18, FontWeight.Medium),
)

// Radii follow hierarchy: small controls 8, slips 10, sheets 20. Not one radius for everything.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(20.dp),
)

val LocalDarkTheme = staticCompositionLocalOf { false }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun TapspendTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(
        LocalHapticsEnabled provides hapticsEnabled,
        LocalDarkTheme provides dark,
        LocalLedger provides if (dark) DarkLedger else LightLedger,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
