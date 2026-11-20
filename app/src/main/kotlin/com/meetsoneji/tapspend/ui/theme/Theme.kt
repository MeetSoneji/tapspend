package com.meetsoneji.tapspend.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.meetsoneji.tapspend.data.ThemeMode

// Futuristic palette: deep space navy, electric cyan primary, violet secondary, glass surfaces.
object Neon {
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Magenta = Color(0xFFE879F9)
    val Mint = Color(0xFF34D399)
}

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF22D3EE), onPrimary = Color(0xFF00252C),
    primaryContainer = Color(0xFF0E3A45), onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = Color(0xFF8B5CF6), onSecondary = Color.White,
    secondaryContainer = Color(0xFF241B4B), onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = Color(0xFFE879F9), onTertiary = Color(0xFF3B0A45),
    tertiaryContainer = Color(0xFF4A1A56), onTertiaryContainer = Color(0xFFFAE8FF),
    background = Color(0xFF070B14), onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0A101C), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2233), onSurfaceVariant = Color(0xFF9AA8BF),
    outline = Color(0xFF3A4660), outlineVariant = Color(0xFF232D42),
    error = Color(0xFFFF6B8A), onError = Color(0xFF2D0010),
    errorContainer = Color(0xFF5A1228), onErrorContainer = Color(0xFFFFD9E1),
    surfaceBright = Color(0xFF1B2438), surfaceDim = Color(0xFF070B14),
    surfaceContainerLowest = Color(0xFF05080F), surfaceContainerLow = Color(0xFF0B1220),
    surfaceContainer = Color(0xFF0F1727), surfaceContainerHigh = Color(0xFF151F33),
    surfaceContainerHighest = Color(0xFF1C2740),
)

val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0891B2), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF5FC), onPrimaryContainer = Color(0xFF00313B),
    secondary = Color(0xFF6D28D9), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7FF), onSecondaryContainer = Color(0xFF22115A),
    tertiary = Color(0xFFA21CAF), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFAE1FF), onTertiaryContainer = Color(0xFF3B0A45),
    background = Color(0xFFF3F6FB), onBackground = Color(0xFF0C1322),
    surface = Color(0xFFF7F9FD), onSurface = Color(0xFF0C1322),
    surfaceVariant = Color(0xFFE1E7F2), onSurfaceVariant = Color(0xFF475671),
    outline = Color(0xFF7886A0), outlineVariant = Color(0xFFCBD4E4),
    error = Color(0xFFD62456), onError = Color.White,
    errorContainer = Color(0xFFFFD9E1), onErrorContainer = Color(0xFF410013),
    surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFD9DFEA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F3FA),
    surfaceContainer = Color(0xFFE9EEF7), surfaceContainerHigh = Color(0xFFE2E8F3),
    surfaceContainerHighest = Color(0xFFDAE1EE),
)

/** Tabular figures so digits do not jump while typing. */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontSize = 24.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp),
        labelLarge = t.labelLarge.copy(fontSize = 14.sp),
    )
}

// 8 dp small, 16 dp medium (cards), 28 dp sheets.
private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

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
    CompositionLocalProvider(LocalHapticsEnabled provides hapticsEnabled, LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) { Backdrop(dark, content) }
    }
}

val LocalDarkTheme = staticCompositionLocalOf { false }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Budget state is never colour alone: it is always paired with text. */
enum class BudgetState { GOOD, WARNING, OVER }

fun budgetState(spent: Long, budget: Long): BudgetState {
    if (budget <= 0) return BudgetState.GOOD
    val pct = spent.toDouble() / budget.toDouble()
    return when {
        pct > 1.0 -> BudgetState.OVER
        pct >= 0.8 -> BudgetState.WARNING
        else -> BudgetState.GOOD
    }
}

@Composable
fun BudgetState.color(): Color = when (this) {
    BudgetState.GOOD -> MaterialTheme.colorScheme.primary
    BudgetState.WARNING -> MaterialTheme.colorScheme.tertiary
    BudgetState.OVER -> MaterialTheme.colorScheme.error
}
