package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.entry.EntryForm
import com.meetsoneji.tapspend.ui.entry.EntryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

class EditViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    var original: ExpenseEntity? = null
        private set
    val entry = MutableStateFlow<EntryState?>(null)
    private val _cats = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _cats
    val missing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val e = c.repository.getExpense(id)
            if (e == null) { missing.value = true; return@launch }
            original = e
            val fd = Money.fractionDigits(e.currency)
            val text = BigDecimal.valueOf(e.amountMinor).movePointLeft(fd).let { if (fd == 0) it.toBigInteger().toString() else it.stripTrailingZeros().toPlainString() }
            watchRefunds()
            entry.value = EntryState(e.currency, text, e.categoryId, e.note ?: "", Instant.ofEpochMilli(e.occurredAt))
            val all = c.repository.allCategories.first()
            _cats.value = all.filter { !it.isArchived || it.id == e.categoryId }
        }
    }

    fun save(categoryId: String, onDone: () -> Unit) {
        val e = original ?: return
        val s = entry.value ?: return
        val minor = s.minor
        if (minor <= 0) { s.shake++; return }
        val zone = java.time.ZoneId.systemDefault()
        val updated = e.copy(
            amountMinor = minor, currency = s.currency, categoryId = categoryId, note = s.note.ifBlank { null },
            occurredAt = s.at.toEpochMilli(), zoneId = zone.id, localDate = s.at.atZone(zone).toLocalDate().toString(),
        )
        c.appScope.launch { c.repository.updateExpense(updated) }
        onDone()
    }

    val refunded = MutableStateFlow(0L)

    fun watchRefunds() = viewModelScope.launch { c.repository.observeRefundedFor(id).collect { refunded.value = it } }

    fun markRefunded(amountMinor: Long, onError: () -> Unit) {
        val e = original ?: return
        c.appScope.launch { runCatching { c.repository.markRefunded(e, amountMinor) }.onFailure { onError() } }
    }

    fun delete(onDone: () -> Unit) {
        c.appScope.launch { c.repository.softDelete(id) }
        onDone()
    }
}

@Composable
fun EditScreen(id: String, onDone: () -> Unit) {
    val vm = appViewModel { EditViewModel(it, id) }
    val entry by vm.entry.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var refundDialog by remember { mutableStateOf(false) }
    val refunded by vm.refunded.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onDone() }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onDone) { Text("← " + stringResource(R.string.back)) }
            Box(Modifier.weight(1f)) { Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.titleLarge) }
            TextButton({ confirmDelete = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        }
        val state = entry
        if (state != null) {
            // In Edit, tapping a chip only selects it (never saves), so a stray tap cannot overwrite an expense.
            EntryForm(state = state, categories = categories, saveOnTap = false, onSave = { vm.save(it, onDone) }, saveLabel = stringResource(R.string.update))
            val original = vm.original
            if (original != null && original.kind == "EXPENSE") {
                if (refunded > 0) Text(
                    stringResource(R.string.refunded_so_far, Money.format(refunded, original.currency)),
                    style = MaterialTheme.typography.bodyMedium, color = com.meetsoneji.tapspend.ui.theme.LocalLedger.current.credit,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (refunded < original.amountMinor) androidx.compose.material3.OutlinedButton({ refundDialog = true }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(stringResource(R.string.mark_refunded))
                }
            }
        }
    }

    val orig = vm.original
    if (refundDialog && orig != null) {
        val fd = Money.fractionDigits(orig.currency)
        var text by remember { mutableStateOf(BigDecimal.valueOf(orig.amountMinor - refunded).movePointLeft(fd).stripTrailingZeros().toPlainString()) }
        AlertDialog(
            onDismissRequest = { refundDialog = false },
            title = { Text(stringResource(R.string.mark_refunded)) },
            text = {
                Column {
                    Text(stringResource(R.string.refund_dialog_body), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.OutlinedTextField(
                        text, { t -> text = t.filter { it.isDigit() || it == '.' }.take(14) }, singleLine = true,
                        prefix = { Text(Money.symbol(orig.currency)) }, modifier = Modifier.padding(top = 8.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    val minor = runCatching { Money.parseMinor(text, orig.currency) }.getOrNull()
                    if (minor != null && minor in 1..(orig.amountMinor - refunded)) { vm.markRefunded(minor) {}; refundDialog = false }
                }) { Text(stringResource(R.string.mark_refunded_confirm)) }
            },
            dismissButton = { TextButton({ refundDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(onDone) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
