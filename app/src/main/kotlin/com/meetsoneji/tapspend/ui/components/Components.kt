package com.meetsoneji.tapspend.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.meetsoneji.tapspend.core.Keypad
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.core.capture.TxnKind
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.ui.HapticKind
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.haptic
import com.meetsoneji.tapspend.ui.rememberAnimationsEnabled
import com.meetsoneji.tapspend.ui.theme.CategoryPalette
import com.meetsoneji.tapspend.ui.theme.LocalHapticsEnabled
import com.meetsoneji.tapspend.ui.theme.LocalLedger
import com.meetsoneji.tapspend.ui.theme.TabularFigures
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Amount as the user types it. Ink, large, tabular; shrinks before it would wrap. */
@Composable
fun AmountDisplay(text: String, currency: String, shakeTrigger: Int, modifier: Modifier = Modifier, onCurrencyLongPress: (() -> Unit)? = null) {
    val animations = rememberAnimationsEnabled()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0 && animations) for (x in listOf(-12f, 12f, -8f, 8f, 0f)) shake.animateTo(x, tween(40, easing = LinearEasing))
    }
    val shown = Money.formatTyping(text, currency)
    val size = when { shown.length <= 8 -> 52.sp; shown.length <= 11 -> 44.sp; shown.length <= 14 -> 34.sp; else -> 28.sp }
    val caret by rememberInfiniteTransition(label = "caret").animateFloat(1f, if (animations) 0f else 1f, infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    val spoken = Money.speech(Keypad.toMinor(text, currency), currency)
    Row(
        modifier.fillMaxWidth().graphicsLayer { translationX = shake.value }.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            shown,
            style = MaterialTheme.typography.displayLarge.merge(TabularFigures).copy(fontSize = size, lineHeight = size * 1.1f),
            color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = if (onCurrencyLongPress != null) Modifier.longPress(onCurrencyLongPress) else Modifier,
        )
        Box(Modifier.padding(start = 3.dp).width(2.dp).height(with(LocalDensity.current) { size.toDp() * 0.8f }).alpha(caret).background(LocalLedger.current.green))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.longPress(onLong: () -> Unit) = combinedClickable(onClick = {}, onLongClick = onLong)

/** 3x4 keypad. Flat keys on the page; a light haptic on each press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(showDecimal: Boolean, onDigit: (Char) -> Unit, onDot: () -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val haptics = LocalHapticsEnabled.current
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val enabled = key != "." || showDecimal
                    val label = when (key) { "⌫" -> stringResource(R.string.keypad_backspace); "." -> stringResource(R.string.keypad_decimal); else -> key }
                    Box(
                        Modifier.weight(1f).heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
                            .background(if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                            .then(
                                if (enabled) Modifier.combinedClickable(
                                    role = Role.Button,
                                    onClick = {
                                        view.haptic(HapticKind.KEY, haptics)
                                        when (key) { "." -> onDot(); "⌫" -> onBackspace(); else -> onDigit(key[0]) }
                                    },
                                    onLongClick = if (key == "⌫") ({ view.haptic(HapticKind.TICK, haptics); onClear() }) else null,
                                ).semantics { contentDescription = label } else Modifier.clearAndSetSemantics { },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (enabled) Text(key, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clearAndSetSemantics { })
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDot(colorKey: String, modifier: Modifier = Modifier, size: Int = 8) {
    Box(modifier.size(size.dp).clip(CircleShape).background(CategoryPalette.color(colorKey)))
}

/** A category as a dot and its name. Selected = filled ink outline; colour never carries meaning alone. */
@Composable
fun CategoryChip(category: CategoryEntity, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = categoryName(category)
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) null else BorderStroke(1.dp, LocalLedger.current.rule),
        modifier = modifier.heightIn(min = 44.dp).semantics { role = Role.Button; contentDescription = name },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(category.colorKey)
            Spacer(Modifier.width(8.dp))
            Text(
                name, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
fun CategoryChipRow(categories: List<CategoryEntity>, selectedId: String?, onSelect: (CategoryEntity) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.take(8).forEach { c -> CategoryChip(c, c.id == selectedId, { onSelect(c) }) }
        if (categories.size > 8) TextButton(onClick = onMore) { Text(stringResource(R.string.more_categories)) }
    }
}

/** Signed amount text: refunds and cashback read as money coming back. */
@Composable
fun signedAmount(e: ExpenseEntity): String {
    val base = Money.format(e.amountMinor, e.currency)
    return if (e.kind == TxnKind.REFUND || e.kind == TxnKind.CASHBACK || e.kind == TxnKind.INCOME) "+$base" else base
}

@Composable
fun kindLabel(kind: String): String? = when (kind) {
    TxnKind.REFUND -> stringResource(R.string.kind_refund)
    TxnKind.CASHBACK -> stringResource(R.string.kind_cashback)
    TxnKind.TRANSFER -> stringResource(R.string.kind_transfer)
    TxnKind.INCOME -> stringResource(R.string.kind_income)
    TxnKind.IGNORED -> stringResource(R.string.kind_ignored)
    else -> null
}

/** A passbook line: what, where it came from, amount on the right in tabular figures. */
@Composable
fun ExpenseRow(expense: ExpenseEntity, category: CategoryEntity?, accountLabel: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val zone = runCatching { ZoneId.of(expense.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val time = Instant.ofEpochMilli(expense.occurredAt).atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val catName = category?.let { categoryName(it) } ?: stringResource(R.string.cat_other)
    val title = expense.merchant ?: expense.note ?: kindLabel(expense.kind) ?: catName
    // Don't repeat the category when it is already the title (manual entries without a note).
    val lead = (kindLabel(expense.kind) ?: catName).takeIf { it != title }
    val second = listOfNotNull(lead, accountLabel, time).joinToString(", ")
    val amount = signedAmount(expense)
    val ledger = LocalLedger.current
    val muted = expense.kind == TxnKind.TRANSFER || expense.kind == TxnKind.IGNORED || expense.kind == TxnKind.INCOME
    Surface(onClick = onClick, color = Color.Transparent, modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title, $amount, $second" }) {
        Row(Modifier.heightIn(min = 60.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(if (muted) "slate" else category?.colorKey ?: "slate", Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                amount, style = MaterialTheme.typography.titleMedium.merge(TabularFigures), textAlign = TextAlign.End, maxLines = 1,
                color = when {
                    muted -> MaterialTheme.colorScheme.onSurfaceVariant
                    expense.kind == TxnKind.REFUND || expense.kind == TxnKind.CASHBACK -> ledger.credit
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "d MMM yyyy", Locale.getDefault()))
    }
}

/** Day heading with that day's total, sentence case, on the page colour. */
@Composable
fun DayHeader(date: LocalDate, totals: List<Pair<Long, String>>, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(dayLabel(date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(totals.joinToString(", ") { (m, c) -> Money.format(m, c) }, style = MaterialTheme.typography.bodyMedium.merge(TabularFigures), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** An empty state says what to do next, plainly. */
@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

/** "‹  November 2026  ›" */
@Composable
fun MonthSwitcher(label: String, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onPrev, modifier = Modifier.semantics { contentDescription = "Previous month" }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        Text(label, style = MaterialTheme.typography.titleSmall)
        TextButton(onNext, enabled = canNext, modifier = Modifier.semantics { contentDescription = "Next month" }) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * The counterfoil: a white slip for something waiting on you. Details above a dashed tear line, decisions below.
 * This is the one bold element in the app; everything else stays flat on the page.
 */
@Composable
fun Slip(modifier: Modifier = Modifier, top: @Composable ColumnScope.() -> Unit, bottom: @Composable () -> Unit) {
    val ledger = LocalLedger.current
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = ledger.slip,
        border = BorderStroke(1.dp, ledger.rule), shadowElevation = 0.dp,
    ) {
        Column {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp), content = top)
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawLine(ledger.rule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) { bottom() }
        }
    }
}
