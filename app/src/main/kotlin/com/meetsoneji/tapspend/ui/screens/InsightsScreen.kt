package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.core.BudgetPeriod
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.core.Period
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.CategoryTotal
import com.meetsoneji.tapspend.data.DayTotal
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.components.EmptyState
import com.meetsoneji.tapspend.ui.theme.CategoryPalette
import com.meetsoneji.tapspend.ui.theme.TabularFigures
import com.meetsoneji.tapspend.ui.theme.GlassCard
import com.meetsoneji.tapspend.ui.theme.HudLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class InsightsState(
    val settings: UserSettings,
    val period: Period,
    val periodLabel: String,
    val categoryTotals: List<CategoryTotal>,
    val dailyTotals: List<DayTotal>,
    val categories: Map<String, CategoryEntity>,
    val currencies: List<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(private val c: AppContainer) : ViewModel() {
    private val offset = MutableStateFlow(0)
    val selectedCurrency = MutableStateFlow<String?>(null)

    val state: StateFlow<InsightsState?> = combine(c.settings.flow, offset) { s, o -> s to o }.flatMapLatest { (s, o) ->
        var p = BudgetPeriod.containing(LocalDate.now(c.clock), s.monthStartDay)
        repeat(-o) { p = BudgetPeriod.previous(p, s.monthStartDay) }
        val label = BudgetPeriod.label(p, s.monthStartDay)
        combine(c.repository.observeCategoryTotals(p), c.repository.observeDailyTotals(p), c.repository.allCategories) { ct, dt, cats ->
            val currencies = (ct.map { it.currency }.distinct().sortedByDescending { cur -> ct.filter { it.currency == cur }.sumOf { it.total } })
            InsightsState(s, p, label, ct, dt, cats.associateBy { it.id }, currencies)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previous() { offset.value -= 1 }
    fun next() { if (offset.value < 0) offset.value += 1 }
    fun canGoNext() = offset.value < 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onCategory: (String) -> Unit) {
    val vm = appViewModel { InsightsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val chosen by vm.selectedCurrency.collectAsStateWithLifecycle()
    val s = state ?: run { Box(Modifier.fillMaxSize()); return }

    val currency = chosen?.takeIf { it in s.currencies } ?: s.currencies.firstOrNull { it == s.settings.currency } ?: s.currencies.firstOrNull() ?: s.settings.currency
    val cats = s.categoryTotals.filter { it.currency == currency }.sortedByDescending { it.total }
    val total = cats.sumOf { it.total }
    val days = s.dailyTotals.filter { it.currency == currency }.associate { it.localDate to it.total }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = vm::previous) { Text("◀", modifier = Modifier.padding(horizontal = 4.dp)) }
                Text(s.periodLabel, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = vm::next, enabled = vm.canGoNext()) { Text("▶", modifier = Modifier.padding(horizontal = 4.dp)) }
            }
        }
        if (s.currencies.size > 1) {
            item {
                PrimaryTabRow(selectedTabIndex = s.currencies.indexOf(currency).coerceAtLeast(0)) {
                    s.currencies.forEach { cur -> Tab(selected = cur == currency, onClick = { vm.selectedCurrency.value = cur }, text = { Text(cur) }) }
                }
            }
        }
        if (cats.isEmpty()) {
            item { EmptyState("📊", stringResource(R.string.empty_insights_title), stringResource(R.string.empty_insights_body)) }
            return@LazyColumn
        }
        item { SummaryCard(s, currency, total, cats) }
        item { DonutCard(cats, s.categories, total, currency) }
        item { DailyBarsCard(s.period, days, currency) }
        item { HudLabel(stringResource(R.string.by_category), Modifier.padding(horizontal = 16.dp)) }
        items(cats, key = { it.categoryId + it.currency }) { ct ->
            val cat = s.categories[ct.categoryId]
            val pct = if (total == 0L) 0 else Math.round(ct.total * 100.0 / total).toInt()
            val name = cat?.let { categoryName(it) } ?: stringResource(R.string.cat_other)
            val spoken = stringResource(R.string.insight_row_description, name, pct, Money.speech(ct.total, currency))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onCategory(ct.categoryId) }.padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).background(CategoryPalette.color(cat?.colorKey ?: "slate"), CircleShape))
                Text(" ${cat?.iconKey ?: "🧾"} $name", modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                Text("$pct%", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                Text(Money.format(ct.total, currency), style = MaterialTheme.typography.titleSmall.merge(TabularFigures), fontWeight = FontWeight.SemiBold)
            }
        }
        item { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SummaryCard(s: InsightsState, currency: String, total: Long, cats: List<CategoryTotal>) {
    val top = cats.firstOrNull()?.let { s.categories[it.categoryId] }
    val budget = s.settings.monthlyBudgetMinor.takeIf { currency == s.settings.currency }
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HudLabel(stringResource(R.string.month_spent))
            Text(Money.format(total, currency), style = MaterialTheme.typography.headlineLarge.merge(TabularFigures))
            if (top != null) Text(stringResource(R.string.top_category, "${top.iconKey} ${categoryName(top)}"), style = MaterialTheme.typography.bodyMedium)
            if (budget != null) {
                val left = budget - total
                Text(
                    if (left >= 0) stringResource(R.string.budget_remaining, Money.format(left, currency))
                    else stringResource(R.string.budget_exceeded_by, Money.format(-left, currency)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DonutCard(cats: List<CategoryTotal>, byId: Map<String, CategoryEntity>, total: Long, currency: String) {
    val dark = com.meetsoneji.tapspend.ui.theme.LocalDarkTheme.current
    val gap = 2f
    val ctx = LocalContext.current
    val summary = cats.take(3).joinToString(", ") { ct ->
        val n = byId[ct.categoryId]?.let { categoryName(ctx, it) } ?: ""
        "$n ${Math.round(ct.total * 100.0 / total)}%"
    }
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp).semantics { contentDescription = summary }) {
                val stroke = 28.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                cats.forEach { ct ->
                    val sweep = 360f * ct.total / total
                    val color = CategoryPalette.color(byId[ct.categoryId]?.colorKey ?: "slate", dark)
                    drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
                Text(Money.format(total, currency), style = MaterialTheme.typography.titleLarge.merge(TabularFigures), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.insights_total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DailyBarsCard(period: Period, days: Map<String, Long>, currency: String) {
    val max = (days.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val barBrush = com.meetsoneji.tapspend.ui.theme.NeonBrush
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val peak = days.maxByOrNull { it.value }
    val summary = stringResource(R.string.daily_bars_description, period.days, peak?.let { Money.speech(it.value, currency) } ?: "0")
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HudLabel(stringResource(R.string.daily_spend))
            Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = summary }) {
                val n = period.days
                val slot = size.width / n
                val barW = slot * 0.62f
                for (i in 0 until n) {
                    val date = period.start.plusDays(i.toLong()).toString()
                    val v = days[date] ?: 0L
                    val h = if (v == 0L) 3.dp.toPx() else (size.height * v / max).coerceAtLeast(6.dp.toPx())
                    val x = i * slot + (slot - barW) / 2
                    if (v == 0L) drawRoundRect(trackColor, Offset(x, size.height - h), Size(barW, h), androidx.compose.ui.geometry.CornerRadius(barW / 2)) else drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(com.meetsoneji.tapspend.ui.theme.Neon.Cyan, com.meetsoneji.tapspend.ui.theme.Neon.Violet), startY = size.height - h, endY = size.height), Offset(x, size.height - h), Size(barW, h), androidx.compose.ui.geometry.CornerRadius(barW / 2))
                }
            }
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(period.start.dayOfMonth.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(period.start.plusDays((period.days / 2).toLong()).dayOfMonth.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(period.end.dayOfMonth.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
