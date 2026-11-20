package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextButton
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
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.components.DayHeader
import com.meetsoneji.tapspend.ui.components.EmptyState
import com.meetsoneji.tapspend.ui.components.ExpenseRow
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
    val settings: UserSettings,
    val period: Period,
    val periodLabel: String,
    val categoryFilter: String?,
    val query: String,
    val expenses: List<ExpenseEntity>,
    val categories: List<CategoryEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val monthOffset = MutableStateFlow(0)
    val categoryFilter = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    val state: StateFlow<HistoryState?> = combine(c.settings.flow, monthOffset, categoryFilter, query.debounce(200)) { s, off, cat, q ->
        Inputs(s, off, cat, q)
    }.flatMapLatest { i ->
        var p = BudgetPeriod.containing(LocalDate.now(c.clock), i.settings.monthStartDay)
        repeat(-i.offset) { p = BudgetPeriod.previous(p, i.settings.monthStartDay) }
        repeat(i.offset.coerceAtLeast(0)) { p = BudgetPeriod.next(p, i.settings.monthStartDay) }
        val label = BudgetPeriod.label(p, i.settings.monthStartDay)
        combine(c.repository.observeHistory(p, i.category, i.query), c.repository.allCategories) { exp, cats ->
            HistoryState(i.settings, p, label, i.category, i.query, exp, cats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Inputs(val settings: UserSettings, val offset: Int, val category: String?, val query: String)

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

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val s = state
        if (s == null) { Box(Modifier.fillMaxSize().padding(padding)); return@Scaffold }
        val byId = s.categories.associateBy { it.id }
        val groups = s.expenses.groupBy { it.localDate }
        LazyColumn(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = vm::previousMonth) { Text("◀", modifier = Modifier.padding(horizontal = 4.dp)) }
                    Text(s.periodLabel, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = vm::nextMonth, enabled = vm.canGoNext()) { Text("▶", modifier = Modifier.padding(horizontal = 4.dp)) }
                }
            }
            item {
                OutlinedTextField(
                    value = s.query, onValueChange = { vm.query.value = it.take(60) }, singleLine = true,
                    label = { Text(stringResource(R.string.search_notes)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = s.categoryFilter == null, onClick = { vm.categoryFilter.value = null }, label = { Text(stringResource(R.string.filter_all)) }) }
                    items(s.categories.filter { !it.isArchived || it.id == s.categoryFilter }, key = { it.id }) { c ->
                        FilterChip(selected = s.categoryFilter == c.id, onClick = { vm.categoryFilter.value = if (s.categoryFilter == c.id) null else c.id }, label = { Text("${c.iconKey} ${categoryName(c)}") })
                    }
                }
            }
            if (s.expenses.isEmpty()) {
                item { EmptyState("🔍", stringResource(R.string.empty_history_title), stringResource(R.string.empty_history_body)) }
            }
            groups.forEach { (date, list) ->
                stickyHeader(key = "h-$date") {
                    val totals = list.groupBy { it.currency }.map { (cur, es) -> es.sumOf { it.amountMinor } to cur }
                    DayHeader(LocalDate.parse(date), totals)
                }
                items(list, key = { it.id }) { e ->
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            vm.delete(e.id)
                            scope.launch {
                                val r = snackbar.showSnackbar(deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                                if (r == SnackbarResult.ActionPerformed) vm.undo(e.id)
                            }
                            true
                        } else false
                    })
                    SwipeToDismissBox(
                        state = dismiss, enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                Text("🗑️ " + stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        },
                    ) {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) { ExpenseRow(e, byId[e.categoryId], onClick = { onEdit(e.id) }) }
                    }
                }
            }
        }
    }
}
