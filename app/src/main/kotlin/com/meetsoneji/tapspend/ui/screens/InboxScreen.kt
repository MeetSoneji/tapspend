package com.meetsoneji.tapspend.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.capture.ReviewNotifier
import com.meetsoneji.tapspend.core.BudgetPeriod
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.core.Period
import com.meetsoneji.tapspend.core.capture.TxnKind
import com.meetsoneji.tapspend.data.AccountEntity
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.CurrencyTotal
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.components.CategoryChip
import com.meetsoneji.tapspend.ui.components.DayHeader
import com.meetsoneji.tapspend.ui.components.EmptyNote
import com.meetsoneji.tapspend.ui.components.ExpenseRow
import com.meetsoneji.tapspend.ui.components.SectionHeader
import com.meetsoneji.tapspend.ui.components.Slip
import com.meetsoneji.tapspend.ui.components.signedAmount
import com.meetsoneji.tapspend.ui.quickLogIntent
import com.meetsoneji.tapspend.ui.theme.LocalLedger
import com.meetsoneji.tapspend.ui.theme.TabularFigures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class InboxState(
    val settings: UserSettings,
    val period: Period,
    val monthTotals: List<CurrencyTotal>,
    val pending: List<ExpenseEntity>,
    val recent: List<ExpenseEntity>,
    val categories: Map<String, CategoryEntity>,
    val activeCategories: List<CategoryEntity>,
    val accounts: Map<String, AccountEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<InboxState?> = c.settings.flow.flatMapLatest { s ->
        val period = BudgetPeriod.containing(LocalDate.now(c.clock), s.monthStartDay)
        combine(
            c.repository.observeTotals(period), c.repository.pending, c.repository.observeRecent(40),
            c.repository.allCategories, c.repository.accounts,
        ) { totals, pending, recent, cats, accounts ->
            InboxState(s, period, totals, pending, recent, cats.associateBy { it.id }, cats.filter { !it.isArchived }, accounts.associateBy { it.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun approve(id: String, categoryId: String? = null, kind: String? = null) = c.appScope.launch {
        c.repository.approve(id, categoryId, kind)
        ReviewNotifier.cancel(c.appContext, id)
    }

    fun approveAll(ids: List<String>) = c.appScope.launch {
        c.repository.approveAllPending()
        ids.forEach { ReviewNotifier.cancel(c.appContext, it) }
    }
}

@Composable
fun InboxScreen(reviewId: String?, onReviewHandled: () -> Unit, onEdit: (String) -> Unit, onSeeAll: () -> Unit, onSetUpCapture: () -> Unit) {
    val vm = appViewModel { InboxViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var reviewing by remember { mutableStateOf<ExpenseEntity?>(null) }

    val s = state
    LaunchedEffect(reviewId, s?.pending) {
        if (reviewId != null && s != null) {
            s.pending.firstOrNull { it.id == reviewId }?.let { reviewing = it }
            onReviewHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { context.startActivity(quickLogIntent(context)) },
                containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface,
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_add_cash)) }
        },
    ) { padding ->
        if (s == null) { Box(Modifier.fillMaxSize().padding(padding)); return@Scaffold }
        val smsOn = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val cur = s.settings.currency
        val spent = s.monthTotals.firstOrNull { it.currency == cur }?.total ?: 0L
        val pendingSpend = s.pending.filter { it.kind == TxnKind.EXPENSE }.sumOf { it.amountMinor }
        val month = s.period.end.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
        val recentByDay = s.recent.filter { it.localDate >= LocalDate.now().minusDays(6).toString() }.groupBy { it.localDate }

        LazyColumn(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.inbox_spent_sentence, Money.format(spent, cur), month),
                        style = MaterialTheme.typography.headlineMedium.merge(TabularFigures),
                    )
                    s.monthTotals.filter { it.currency != cur && it.total != 0L }.forEach {
                        Text(stringResource(R.string.inbox_other_currency, Money.format(it.total, it.currency)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (pendingSpend > 0) {
                        Text(stringResource(R.string.inbox_waiting, Money.format(pendingSpend, cur)), style = MaterialTheme.typography.bodyMedium, color = LocalLedger.current.due)
                    }
                }
            }

            if (s.pending.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.inbox_to_review_count, s.pending.size)) {
                        if (s.pending.size > 1) TextButton({ vm.approveAll(s.pending.map { it.id }) }) { Text(stringResource(R.string.inbox_confirm_all)) }
                    }
                }
                item {
                    val first = s.pending.first()
                    ReviewSlip(
                        first, s.categories[first.categoryId], first.accountId?.let { s.accounts[it]?.label },
                        onApprove = { vm.approve(first.id) }, onChange = { reviewing = first },
                        onNotSpending = { vm.approve(first.id, kind = TxnKind.TRANSFER) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                items(s.pending.drop(1), key = { "p-" + it.id }) { e ->
                    PendingRow(e, s.categories[e.categoryId], e.accountId?.let { s.accounts[it]?.label }, onApprove = { vm.approve(e.id) }, onOpen = { reviewing = e })
                }
            } else if (smsOn) {
                item { EmptyNote(stringResource(R.string.inbox_all_clear_title), stringResource(R.string.inbox_all_clear_body)) }
            }

            if (!smsOn) {
                item {
                    Slip(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        top = {
                            Text(stringResource(R.string.capture_off_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.capture_off_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        bottom = { Button(onSetUpCapture) { Text(stringResource(R.string.capture_turn_on)) } },
                    )
                }
            }

            if (recentByDay.isEmpty() && s.pending.isEmpty()) {
                item { EmptyNote(stringResource(R.string.empty_home_title), stringResource(R.string.empty_home_body)) }
            }
            recentByDay.forEach { (date, list) ->
                item(key = "d-$date") { DayHeader(LocalDate.parse(date), list.groupBy { it.currency }.map { (c, es) -> es.sumOf { if (it.kind == TxnKind.EXPENSE) it.amountMinor else -it.amountMinor } to c }) }
                items(list, key = { it.id }) { e -> ExpenseRow(e, s.categories[e.categoryId], e.accountId?.let { s.accounts[it]?.label }, onClick = { onEdit(e.id) }) }
            }
            if (s.recent.isNotEmpty()) item { TextButton(onSeeAll, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) { Text(stringResource(R.string.see_all_spends)) } }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    reviewing?.let { e ->
        val st = state ?: return@let
        ReviewSheet(
            e, st.activeCategories, onDismiss = { reviewing = null },
            onPick = { categoryId, kind -> vm.approve(e.id, categoryId, kind); reviewing = null },
        )
    }
}

@Composable
private fun shortGuess(e: ExpenseEntity, category: CategoryEntity?): String = when (e.kind) {
    TxnKind.TRANSFER -> stringResource(R.string.kind_transfer)
    TxnKind.REFUND -> stringResource(R.string.kind_refund) + (category?.let { ", " + categoryName(it) } ?: "")
    TxnKind.CASHBACK -> stringResource(R.string.kind_cashback)
    else -> category?.let { categoryName(it) } ?: stringResource(R.string.cat_other)
}

@Composable
private fun titleOf(e: ExpenseEntity): String = e.merchant ?: when (e.kind) {
    TxnKind.CASHBACK -> stringResource(R.string.kind_cashback)
    TxnKind.REFUND -> stringResource(R.string.kind_refund)
    else -> stringResource(R.string.unknown_payee)
}

@Composable
private fun guessLine(e: ExpenseEntity, category: CategoryEntity?): String = when (e.kind) {
    TxnKind.TRANSFER -> stringResource(R.string.guess_transfer)
    TxnKind.REFUND -> stringResource(R.string.guess_refund, category?.let { categoryName(it) } ?: "")
    TxnKind.CASHBACK -> stringResource(R.string.guess_cashback)
    else -> category?.let { stringResource(R.string.guess_category, categoryName(it)) } ?: stringResource(R.string.guess_unknown)
}

private fun timeOf(e: ExpenseEntity): String {
    val zone = runCatching { ZoneId.of(e.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val at = Instant.ofEpochMilli(e.occurredAt).atZone(zone)
    val fmt = if (at.toLocalDate() == LocalDate.now()) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) else DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault())
    return at.format(fmt)
}

@Composable
private fun ReviewSlip(e: ExpenseEntity, category: CategoryEntity?, account: String?, onApprove: () -> Unit, onChange: () -> Unit, onNotSpending: () -> Unit, modifier: Modifier = Modifier) {
    val ledger = LocalLedger.current
    Slip(
        modifier,
        top = {
            Row(verticalAlignment = Alignment.Top) {
                Text(titleOf(e), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.padding(start = 12.dp))
                Text(signedAmount(e), style = MaterialTheme.typography.titleLarge.merge(TabularFigures))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(account ?: stringResource(R.string.unknown_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(timeOf(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(guessLine(e, category), style = MaterialTheme.typography.bodyMedium)
        },
        bottom = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onApprove, colors = ButtonDefaults.buttonColors(containerColor = ledger.green)) {
                    Text(
                        when (e.kind) {
                            TxnKind.TRANSFER -> stringResource(R.string.slip_confirm_not_spending)
                            TxnKind.EXPENSE -> stringResource(R.string.slip_save_as, category?.let { categoryName(it) } ?: stringResource(R.string.cat_other))
                            else -> stringResource(R.string.notif_action_confirm)
                        },
                    )
                }
                TextButton(onChange) { Text(stringResource(R.string.notif_action_change)) }
                if (e.kind == TxnKind.EXPENSE) TextButton(onNotSpending) { Text(stringResource(R.string.notif_action_not_spending)) }
            }
        },
    )
}

@Composable
private fun PendingRow(e: ExpenseEntity, category: CategoryEntity?, account: String?, onApprove: () -> Unit, onOpen: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onOpen, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(titleOf(e), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(signedAmount(e), style = MaterialTheme.typography.bodyLarge.merge(TabularFigures))
                }
                Text(listOfNotNull(shortGuess(e, category), account).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onApprove) { Text(stringResource(R.string.notif_action_confirm)) }
        }
    }
}

/** Change a detected transaction: pick a category, or say it isn't spending. One tap decides. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewSheet(e: ExpenseEntity, categories: List<CategoryEntity>, onDismiss: () -> Unit, onPick: (categoryId: String?, kind: String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val credit = e.kind == TxnKind.REFUND || e.kind == TxnKind.CASHBACK || e.kind == TxnKind.INCOME
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(e.merchant ?: stringResource(R.string.unknown_payee), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(signedAmount(e), style = MaterialTheme.typography.titleLarge.merge(TabularFigures))
            }
            Text(stringResource(if (credit) R.string.sheet_refund_for else R.string.sheet_spent_on), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c -> CategoryChip(c, c.id == e.categoryId && e.kind == TxnKind.EXPENSE, { onPick(c.id, if (credit) TxnKind.REFUND else TxnKind.EXPENSE) }) }
            }
            Text(stringResource(R.string.sheet_not_spending), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (credit) {
                    OutlinedButton({ onPick(null, TxnKind.CASHBACK) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheet_cashback)) }
                    OutlinedButton({ onPick(null, TxnKind.INCOME) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheet_income)) }
                } else {
                    OutlinedButton({ onPick(null, TxnKind.TRANSFER) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheet_self_transfer)) }
                    OutlinedButton({ onPick(null, TxnKind.TRANSFER) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheet_card_bill)) }
                }
                OutlinedButton({ onPick(null, TxnKind.IGNORED) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheet_ignore)) }
            }
            Text(stringResource(R.string.sheet_learn_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
