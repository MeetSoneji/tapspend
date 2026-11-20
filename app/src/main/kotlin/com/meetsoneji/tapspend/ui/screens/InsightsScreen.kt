package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.meetsoneji.tapspend.data.CurrencyTotal
import com.meetsoneji.tapspend.data.DayTotal
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.components.EmptyNote
import com.meetsoneji.tapspend.ui.components.MonthSwitcher
import com.meetsoneji.tapspend.ui.components.SectionHeader
import com.meetsoneji.tapspend.ui.theme.CategoryPalette
import com.meetsoneji.tapspend.ui.theme.LocalLedger
import com.meetsoneji.tapspend.ui.theme.TabularFigures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class InsightsState(
    val settings: UserSettings, val period: Period, val periodLabel: String, val totals: List<CurrencyTotal>,
    val categoryTotals: List<CategoryTotal>, val cashback: List<CurrencyTotal>, val daily: List<DayTotal>, val categories: Map<String, CategoryEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(private val c: AppContainer) : ViewModel() {
    private val offset = MutableStateFlow(0)
    val state: StateFlow<InsightsState?> = combine(c.settings.flow, offset) { s, o -> s to o }.flatMapLatest { (s, o) ->
        var p = BudgetPeriod.containing(LocalDate.now(c.clock), s.monthStartDay)
        repeat(-o) { p = BudgetPeriod.previous(p, s.monthStartDay) }
        val label = BudgetPeriod.label(p, s.monthStartDay)
        combine(c.repository.observeTotals(p), c.repository.observeCategoryTotals(p), c.repository.observeCashback(p), c.repository.observeDailyTotals(p), c.repository.allCategories) { t, ct, cb, d, cats ->
            InsightsState(s, p, label, t, ct, cb, d, cats.associateBy { it.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun previous() { offset.value -= 1 }
    fun next() { if (offset.value < 0) offset.value += 1 }
    fun canGoNext() = offset.value < 0
}

@Composable
fun InsightsScreen(onCategory: (String) -> Unit) {
    val vm = appViewModel { InsightsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: run { Box(Modifier.fillMaxSize()); return }
    val cur = s.settings.currency
    val total = s.totals.firstOrNull { it.currency == cur }?.total ?: 0L
    val cats = s.categoryTotals.filter { it.currency == cur && it.total > 0 }.sortedByDescending { it.total }
    val max = cats.maxOfOrNull { it.total } ?: 1L
    val cashback = s.cashback.firstOrNull { it.currency == cur }?.total ?: 0L
    val month = s.period.end.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_insights), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                MonthSwitcher(s.periodLabel, vm::previous, vm::next, vm.canGoNext())
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.inbox_spent_sentence, Money.format(total, cur), month), style = MaterialTheme.typography.headlineSmall.merge(TabularFigures))
                if (cashback > 0) Text(stringResource(R.string.insights_cashback_line, Money.format(cashback, cur)), style = MaterialTheme.typography.bodyMedium, color = LocalLedger.current.credit)
            }
        }
        if (cats.isEmpty()) {
            item { EmptyNote(stringResource(R.string.empty_insights_title), stringResource(R.string.empty_insights_body)) }
            return@LazyColumn
        }
        item { SectionHeader(stringResource(R.string.by_category)) }
        items(cats, key = { it.categoryId + it.currency }) { ct ->
            val c = s.categories[ct.categoryId]
            val name = c?.let { categoryName(it) } ?: stringResource(R.string.cat_other)
            val pct = if (total <= 0) 0 else Math.round(ct.total * 100.0 / total).toInt()
            Column(
                Modifier.fillMaxWidth().clickable { onCategory(ct.categoryId) }.padding(horizontal = 20.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "$name, $pct percent, ${Money.speech(ct.total, cur)}" },
            ) {
                Row {
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(Money.format(ct.total, cur), style = MaterialTheme.typography.bodyLarge.merge(TabularFigures))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(ct.total.toFloat() / max).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CategoryPalette.color(c?.colorKey ?: "slate")))
                }
            }
        }
        item { SectionHeader(stringResource(R.string.daily_spend)) }
        item { DailyBars(s, cur) }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DailyBars(s: InsightsState, cur: String) {
    val days = s.daily.filter { it.currency == cur }.associate { it.localDate to it.total }
    val max = (days.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val ink = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val peak = days.maxByOrNull { it.value }
    val desc = stringResource(R.string.daily_bars_description, s.period.days, peak?.let { Money.speech(it.value, cur) } ?: "0")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = desc }) {
            val n = s.period.days
            val slot = size.width / n
            val w = slot * 0.6f
            for (i in 0 until n) {
                val v = days[s.period.start.plusDays(i.toLong()).toString()] ?: 0L
                val h = if (v <= 0L) 2.dp.toPx() else (size.height * v / max).coerceAtLeast(4.dp.toPx())
                drawRoundRect(if (v <= 0L) track else ink, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 3))
            }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(s.period.start, s.period.start.plusDays((s.period.days / 2).toLong()), s.period.end).forEach {
                Text(it.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
