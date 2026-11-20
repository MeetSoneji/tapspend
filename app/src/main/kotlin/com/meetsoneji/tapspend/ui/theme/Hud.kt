package com.meetsoneji.tapspend.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NeonBrush: Brush get() = Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet))

/** Soft cyan and violet glows behind everything, so flat surfaces feel like a lit HUD. */
@Composable
fun Backdrop(dark: Boolean, content: @Composable () -> Unit) {
    val a = if (dark) 0.16f else 0.08f
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).drawBehind {
            drawRect(Brush.radialGradient(listOf(Neon.Cyan.copy(alpha = a), Color.Transparent), Offset(0f, 0f), size.maxDimension * 0.75f))
            drawRect(Brush.radialGradient(listOf(Neon.Violet.copy(alpha = a), Color.Transparent), Offset(size.width, size.height), size.maxDimension * 0.7f))
        },
    ) {
        // The backdrop replaces the opaque Surface, so it must provide the default text colour itself.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) { content() }
    }
}

/** Translucent surface with a thin gradient edge. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Neon.Cyan.copy(alpha = 0.55f), Neon.Violet.copy(alpha = 0.18f), Neon.Cyan.copy(alpha = 0.10f)))),
    ) { Column(content = content) }
}

/** Small monospace, wide-tracked caps label for a HUD feel. */
@Composable
fun HudLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(), modifier = modifier, color = color,
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium),
    )
}

/** The app mark: a glowing rupee in a gradient ring. */
@Composable
fun BrandMark(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).drawBehind {
            drawCircle(Brush.radialGradient(listOf(Neon.Cyan.copy(alpha = 0.35f), Color.Transparent)), radius = this.size.minDimension * 0.9f)
        }.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .then(Modifier.drawBehind { drawCircle(NeonBrush, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())) }),
        contentAlignment = Alignment.Center,
    ) {
        Text("₹", modifier = Modifier.clearAndSetSemantics { }, style = TextStyle(brush = NeonBrush, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold))
    }
}
