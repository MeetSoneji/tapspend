package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.core.BudgetPeriod
import com.meetsoneji.tapspend.core.Period
import com.meetsoneji.tapspend.core.capture.TxnKind
import com.meetsoneji.tapspend.data.AccountEntity
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.components.CategoryDot
import com.meetsoneji.tapspend.ui.components.DayHeader
import com.meetsoneji.tapspend.ui.components.EmptyNote
import com.meetsoneji.tapspend.ui.components.ExpenseRow
import com.meetsoneji.tapspend.ui.components.MonthSwitcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoryState(
    val settings: UserSettings, val period: Period, val periodLabel: String, val categoryFilter: String?, val query: String,
    val includeTransfers: Boolean, val expenses: List<ExpenseEntity>, val categories: List<CategoryEntity>, val accounts: Map<String, AccountEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val monthOffset = MutableStateFlow(0)
    val categoryFilter = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    val includeTransfers = MutableStateFlow(false)

    private data class In(val s: UserSettings, val off: Int, val cat: String?, val q: String, val t: Boolean)

    val state: StateFlow<HistoryState?> = combine(c.settings.flow, monthOffset, categoryFilter, query.debounce(200), includeTransfers) { s, o, cat, q, t -> In(s, o, cat, q, t) }
        .flatMapLatest { i ->
            var p = BudgetPeriod.containing(LocalDate.now(c.clock), i.s.monthStartDay)
            repeat(-i.off) { p = BudgetPeriod.previous(p, i.s.monthStartDay) }
            val label = BudgetPeriod.label(p, i.s.monthStartDay)
            combine(c.repository.observeHistory(p, i.cat, i.q, i.t), c.repository.allCategories, c.repository.accounts) { exp, cats, acc ->
                HistoryState(i.s, p, label, i.cat, i.q, i.t, exp, cats, acc.associateBy { it.id })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() { monthOffset.value -= 1 }
    fun nextMonth() { if (monthOffset.value < 0) monthOffset.value += 1 }
    fun canGoNext() = monthOffset.value < 0
    fun delete(id: String) { c.appScope.launch { c.repository.softDelete(id) } }
    fun undo(id: String) { c.appScope.launch { c.repository.undoDelete(id) } }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(initialCategoryId: String?, onEdit: (String) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMsg = stringResource(R.string.deleted_msg)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(initialCategoryId) { if (initialCategoryId != null) vm.categoryFilter.value = initialCategoryId }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val s = state ?: run { Box(Modifier.fillMaxSize().padding(padding)); return@Scaffold }
        val groups = s.expenses.groupBy { it.localDate }
        LazyColumn(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    MonthSwitcher(s.periodLabel, vm::previousMonth, vm::nextMonth, vm.canGoNext())
                }
            }
            item {
                OutlinedTextField(
                    value = s.query, onValueChange = { vm.query.value = it.take(60) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_spends)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = s.categoryFilter == null, onClick = { vm.categoryFilter.value = null }, label = { Text(stringResource(R.string.filter_all)) }) }
                    items(s.categories.filter { !it.isArchived || it.id == s.categoryFilter }, key = { it.id }) { c ->
                        FilterChip(
                            selected = s.categoryFilter == c.id, onClick = { vm.categoryFilter.value = if (s.categoryFilter == c.id) null else c.id },
                            leadingIcon = { CategoryDot(c.colorKey) }, label = { Text(categoryName(c)) },
                        )
                    }
                    item { FilterChip(selected = s.includeTransfers, onClick = { vm.includeTransfers.value = !s.includeTransfers }, label = { Text(stringResource(R.string.filter_show_transfers)) }) }
                }
            }
            if (s.expenses.isEmpty()) item { EmptyNote(stringResource(R.string.empty_history_title), stringResource(R.string.empty_history_body)) }
            val byId = s.categories.associateBy { it.id }
            groups.forEach { (date, list) ->
                stickyHeader(key = "h-$date") {
                    DayHeader(LocalDate.parse(date), list.filter { it.kind != TxnKind.TRANSFER && it.kind != TxnKind.IGNORED && it.kind != TxnKind.INCOME }
                        .groupBy { it.currency }.map { (cur, es) -> es.sumOf { if (it.kind == TxnKind.EXPENSE) it.amountMinor else -it.amountMinor } to cur })
                }
                items(list, key = { it.id }) { e ->
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                        if (v == SwipeToDismissBoxValue.EndToStart) {
                            vm.delete(e.id)
                            scope.launch { if (snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undo(e.id) }
                            true
                        } else false
                    })
                    SwipeToDismissBox(
                        state = dismiss, enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                            }
                        },
                    ) {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                            ExpenseRow(e, byId[e.categoryId], e.accountId?.let { s.accounts[it]?.label }, onClick = { onEdit(e.id) })
                        }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}
