package com.meetsoneji.tapspend.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.ui.HapticKind
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.haptic
import com.meetsoneji.tapspend.ui.rememberAnimationsEnabled
import com.meetsoneji.tapspend.ui.theme.BudgetState
import com.meetsoneji.tapspend.ui.theme.CategoryPalette
import com.meetsoneji.tapspend.ui.theme.LocalHapticsEnabled
import com.meetsoneji.tapspend.ui.theme.TabularFigures
import com.meetsoneji.tapspend.ui.theme.budgetState
import com.meetsoneji.tapspend.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The amount is always the biggest thing on screen. It shrinks before it wraps (also at 200% font scale). */
@Composable
fun AmountDisplay(
    text: String,
    currency: String,
    shakeTrigger: Int,
    modifier: Modifier = Modifier,
    onCurrencyLongPress: (() -> Unit)? = null,
) {
    val animations = rememberAnimationsEnabled()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0 && animations) {
            // 200 ms shake for an invalid (zero) amount.
            for (target in listOf(-14f, 14f, -9f, 9f, 0f)) shake.animateTo(target, tween(40, easing = LinearEasing))
        }
    }
    val shown = Money.formatTyping(text, currency)
    val size = when {
        shown.length <= 8 -> 56.sp
        shown.length <= 11 -> 46.sp
        shown.length <= 14 -> 36.sp
        else -> 28.sp
    }
    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f, targetValue = if (animations) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "caretAlpha",
    )
    val minor = com.meetsoneji.tapspend.core.Keypad.toMinor(text, currency)
    val spoken = Money.speech(minor, currency)
    Row(
        modifier = modifier.fillMaxWidth().graphicsLayer { translationX = shake.value }
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shown,
            style = MaterialTheme.typography.displayMedium.merge(TabularFigures).copy(fontSize = size, fontWeight = FontWeight.SemiBold).let { if (text.isEmpty()) it else it.copy(brush = com.meetsoneji.tapspend.ui.theme.NeonBrush) },
            color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = if (onCurrencyLongPress != null) Modifier.combinedClickableLong(onCurrencyLongPress) else Modifier,
        )
        Box(
            Modifier.padding(start = 2.dp).width(3.dp).height(with(androidx.compose.ui.platform.LocalDensity.current) { size.toDp() * 0.9f })
                .alpha(caretAlpha).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableLong(onLong: () -> Unit): Modifier =
    this.combinedClickable(onClick = {}, onLongClick = onLong, onLongClickLabel = null)


/** 3x4 grid, 64 dp minimum keys, a KEYBOARD_TAP haptic on every press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(
    showDecimal: Boolean,
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val haptics = LocalHapticsEnabled.current
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫"))
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val enabled = key != "." || showDecimal
                    val label = when (key) {
                        "⌫" -> stringResource(R.string.keypad_backspace)
                        "." -> stringResource(R.string.keypad_decimal)
                        else -> key
                    }
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(32.dp))
                            .background(if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f) else Color.Transparent)
                            .then(if (enabled) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(32.dp)) else Modifier)
                            .then(
                                if (enabled) {
                                    Modifier.combinedClickable(
                                        role = Role.Button,
                                        onClick = {
                                            view.haptic(HapticKind.KEY, haptics)
                                            when (key) {
                                                "." -> onDot()
                                                "⌫" -> onBackspace()
                                                else -> onDigit(key[0])
                                            }
                                        },
                                        onLongClick = if (key == "⌫") ({
                                            view.haptic(HapticKind.TICK, haptics)
                                            onClear()
                                        }) else null,
                                    )
                                } else Modifier,
                            )
                            .then(if (enabled) Modifier.semantics { contentDescription = label } else Modifier.clearAndSetSemantics { }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (enabled) {
                            Text(
                                key, fontSize = 26.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Icon (emoji) + label, coloured from a palette key. Colour never carries meaning alone: the label is always there. */
@Composable
fun CategoryChip(category: CategoryEntity, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = CategoryPalette.color(category.colorKey)
    val shape = RoundedCornerShape(50)
    val name = categoryName(category)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) accent.copy(alpha = 0.30f) else accent.copy(alpha = 0.12f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else accent.copy(alpha = 0.55f)),
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.Button; contentDescription = name },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(category.iconKey, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/** Horizontally scrollable, max 8 shown, then "Aur" which opens the full list. */
@Composable
fun CategoryChipRow(
    categories: List<CategoryEntity>,
    selectedId: String?,
    onSelect: (CategoryEntity) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.take(8).forEach { c -> CategoryChip(c, c.id == selectedId, { onSelect(c) }) }
        if (categories.size > 8) {
            Surface(
                onClick = onMore, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.more_categories), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Canvas arc. State colour + centre text ("₹4,200 bache hain"), so colour is never the only signal. */
@Composable
fun BudgetRing(spentMinor: Long, budgetMinor: Long, currency: String, modifier: Modifier = Modifier) {
    val state = budgetState(spentMinor, budgetMinor)
    val color = state.color()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fraction = if (budgetMinor <= 0) 0f else (spentMinor.toFloat() / budgetMinor.toFloat()).coerceIn(0f, 1f)
    val remaining = budgetMinor - spentMinor
    val centre = if (remaining >= 0) Money.format(remaining, currency) else Money.format(-remaining, currency)
    val caption = if (remaining >= 0) stringResource(R.string.budget_left) else stringResource(R.string.budget_over)
    val description = stringResource(R.string.budget_ring_description, Money.speech(spentMinor, currency), Money.speech(budgetMinor, currency), (fraction * 100).toInt())
    Box(modifier.size(132.dp).semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(132.dp)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0f) {
                val brush = if (state == BudgetState.GOOD) {
                    androidx.compose.ui.graphics.Brush.sweepGradient(listOf(com.meetsoneji.tapspend.ui.theme.Neon.Violet, com.meetsoneji.tapspend.ui.theme.Neon.Cyan), center)
                } else androidx.compose.ui.graphics.SolidColor(color)
                rotate(-90f) {
                    // soft glow under the arc, then the crisp arc
                    drawArc(color.copy(alpha = 0.22f), 0f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke * 1.9f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centre, style = MaterialTheme.typography.titleMedium.merge(TabularFigures), fontWeight = FontWeight.Bold, maxLines = 1, color = if (state == BudgetState.OVER) color else MaterialTheme.colorScheme.onSurface)
            com.meetsoneji.tapspend.ui.theme.HudLabel(caption)
        }
    }
}

@Composable
fun ExpenseRow(expense: ExpenseEntity, category: CategoryEntity?, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    val zone = runCatching { ZoneId.of(expense.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val at = Instant.ofEpochMilli(expense.occurredAt).atZone(zone)
    val time = if (showDate) at.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)) else at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val name = category?.let { categoryName(it) } ?: stringResource(R.string.cat_other)
    val amount = Money.format(expense.amountMinor, expense.currency)
    val description = stringResource(R.string.expense_row_description, name, Money.speech(expense.amountMinor, expense.currency), time)
    Surface(onClick = onClick, color = Color.Transparent, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val accent = CategoryPalette.color(category?.colorKey ?: "slate")
            Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(category?.iconKey ?: "🧾", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.note ?: name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (expense.note != null) "$name · $time" else time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(amount, style = MaterialTheme.typography.titleMedium.merge(TabularFigures), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 1)
        }
    }
}

@Composable
fun DayHeader(date: LocalDate, totals: List<Pair<Long, String>>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            com.meetsoneji.tapspend.ui.theme.HudLabel(label, Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            Text(totals.joinToString(" · ") { (m, c) -> Money.format(m, c) }, style = MaterialTheme.typography.labelLarge.merge(TabularFigures), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(emoji, fontSize = 48.sp, modifier = Modifier.clearAndSetSemantics { })
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    com.meetsoneji.tapspend.ui.theme.HudLabel(text, modifier.padding(horizontal = 16.dp, vertical = 10.dp), MaterialTheme.colorScheme.primary)
}
