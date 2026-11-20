package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.meetsoneji.tapspend.data.CurrencyTotal
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.components.BudgetRing
import com.meetsoneji.tapspend.ui.components.EmptyState
import com.meetsoneji.tapspend.ui.components.ExpenseRow
import com.meetsoneji.tapspend.ui.quickLogIntent
import com.meetsoneji.tapspend.ui.theme.BudgetState
import com.meetsoneji.tapspend.ui.theme.TabularFigures
import com.meetsoneji.tapspend.ui.theme.GlassCard
import com.meetsoneji.tapspend.ui.theme.HudLabel
import com.meetsoneji.tapspend.ui.theme.budgetState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime

data class HomeState(
    val settings: UserSettings,
    val period: Period,
    val monthTotals: List<CurrencyTotal>,
    val todayTotals: List<CurrencyTotal>,
    val recent: List<ExpenseEntity>,
    val categories: Map<String, CategoryEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState?> = c.settings.flow.flatMapLatest { s ->
        val period = BudgetPeriod.containing(LocalDate.now(c.clock), s.monthStartDay)
        combine(
            c.repository.observeTotals(period), c.repository.observeToday(),
            c.repository.observeRecent(5), c.repository.allCategories,
        ) { month, today, recent, cats -> HomeState(s, period, month, today, recent, cats.associateBy { it.id }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(onEdit: (String) -> Unit, onSeeAll: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { context.startActivity(quickLogIntent(context)) },
                containerColor = com.meetsoneji.tapspend.ui.theme.Neon.Cyan, contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_log)) },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Greeting(s) }
            item { MonthCard(s) }
            item { TodayCard(s) }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (s.recent.isNotEmpty()) TextButton(onSeeAll) { Text(stringResource(R.string.see_all)) }
                }
            }
            if (s.recent.isEmpty()) {
                item { EmptyState("🪙", stringResource(R.string.empty_home_title), stringResource(R.string.empty_home_body)) }
            } else {
                items(s.recent, key = { it.id }) { e -> ExpenseRow(e, s.categories[e.categoryId], onClick = { onEdit(e.id) }) }
            }
            item { Box(Modifier.padding(bottom = 88.dp)) }
        }
    }
}

@Composable
private fun Greeting(s: HomeState) {
    val hour = LocalTime.now().hour
    val greet = stringResource(
        when {
            hour < 5 -> R.string.greet_night
            hour < 12 -> R.string.greet_morning
            hour < 17 -> R.string.greet_afternoon
            hour < 21 -> R.string.greet_evening
            else -> R.string.greet_night
        },
    )
    val name = s.settings.userName
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(if (name.isBlank()) greet else "$greet, $name", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthCard(s: HomeState) {
    val cur = s.settings.currency
    val budget = s.settings.monthlyBudgetMinor
    val spentDefault = s.monthTotals.firstOrNull { it.currency == cur }?.total ?: 0L
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HudLabel(stringResource(R.string.month_spent))
                if (s.monthTotals.isEmpty()) {
                    Text(Money.format(0, cur), style = MaterialTheme.typography.displaySmall.merge(TabularFigures))
                } else {
                    // Totals are never converted: one figure per currency used this month.
                    s.monthTotals.forEach { Text(Money.format(it.total, it.currency), style = MaterialTheme.typography.displaySmall.merge(TabularFigures), maxLines = 1) }
                }
                if (budget != null) {
                    val msg = when (budgetState(spentDefault, budget)) {
                        BudgetState.GOOD -> R.string.budget_msg_good
                        BudgetState.WARNING -> R.string.budget_msg_warning
                        BudgetState.OVER -> R.string.budget_msg_over
                    }
                    Text(stringResource(msg), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.budget_not_set), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (budget != null) BudgetRing(spentDefault, budget, cur)
        }
    }
}

@Composable
private fun TodayCard(s: HomeState) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            HudLabel(stringResource(R.string.today_label), Modifier.weight(1f))
            val text = if (s.todayTotals.isEmpty()) stringResource(R.string.today_nothing)
            else s.todayTotals.joinToString(" · ") { Money.format(it.total, it.currency) }
            Text(text, style = MaterialTheme.typography.titleMedium.merge(TabularFigures), color = MaterialTheme.colorScheme.primary)
        }
    }
}
