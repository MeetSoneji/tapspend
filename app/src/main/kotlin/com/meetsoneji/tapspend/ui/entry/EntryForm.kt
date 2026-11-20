package com.meetsoneji.tapspend.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.core.Keypad
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.ui.HapticKind
import com.meetsoneji.tapspend.ui.components.AmountDisplay
import com.meetsoneji.tapspend.ui.components.CategoryChip
import com.meetsoneji.tapspend.ui.components.CategoryChipRow
import com.meetsoneji.tapspend.ui.components.Keypad
import com.meetsoneji.tapspend.ui.haptic
import com.meetsoneji.tapspend.ui.theme.LocalHapticsEnabled
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything the keypad form edits. Held by a ViewModel so rotation never loses a half-typed amount. */
class EntryState(
    currency: String,
    amount: String = "",
    category: String? = null,
    note: String = "",
    at: Instant = Instant.now(),
) {
    var text by mutableStateOf(amount)
    var currency by mutableStateOf(currency)
    var categoryId by mutableStateOf(category)
    var note by mutableStateOf(note)
    var noteOpen by mutableStateOf(note.isNotEmpty())
    var at by mutableStateOf(at)
    var shake by mutableIntStateOf(0)
    var currencyTouched = false

    val minor: Long get() = Keypad.toMinor(text, currency)

    fun digit(c: Char) { text = Keypad.digit(text, c, currency) }
    fun dot() { text = Keypad.dot(text, currency) }
    fun backspace() { text = Keypad.backspace(text) }
    fun clear() { text = Keypad.clear() }

    fun changeCurrency(code: String) {
        currency = code
        text = Keypad.retarget(text, code)
    }
}

/**
 * The hero UI: amount, category chips, optional note and date, keypad, Save.
 * Used by the Quick Log sheet and (full screen) by Edit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryForm(
    state: EntryState,
    categories: List<CategoryEntity>,
    saveOnTap: Boolean,
    onSave: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
    saveLabel: String = stringResource(R.string.save),
) {
    val view = LocalView.current
    val haptics = LocalHapticsEnabled.current
    var showMore by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    fun reject() {
        state.shake++
        view.haptic(HapticKind.REJECT, haptics)
    }

    fun choose(c: CategoryEntity) {
        if (saveOnTap) {
            if (state.minor <= 0) reject() else onSave(c.id)
        } else {
            view.haptic(HapticKind.TICK, haptics)
            state.categoryId = c.id
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            AssistChip(onClick = { showDate = true }, label = { Text(dateLabel(state.at)) })
            Row {
                AssistChip(onClick = { showCurrency = true }, label = { Text(state.currency) })
                Spacer(Modifier.padding(4.dp))
                AssistChip(
                    onClick = { state.noteOpen = !state.noteOpen },
                    label = { Text(stringResource(if (state.noteOpen) R.string.note_hide else R.string.note_add)) },
                )
            }
        }

        AmountDisplay(
            text = state.text, currency = state.currency, shakeTrigger = state.shake,
            onCurrencyLongPress = { showCurrency = true },
        )

        if (state.noteOpen) {
            OutlinedTextField(
                value = state.note, onValueChange = { state.note = it.take(200) },
                label = { Text(stringResource(R.string.note_label)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        CategoryChipRow(categories, state.categoryId, ::choose, onMore = { showMore = true })

        Keypad(
            showDecimal = Money.fractionDigits(state.currency) > 0,
            onDigit = state::digit, onDot = state::dot, onBackspace = state::backspace, onClear = state::clear,
        )

        if (saveOnTap && state.categoryId == null) {
            Text(
                stringResource(R.string.pick_category_to_save), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Button(
                onClick = { state.categoryId?.let(onSave) },
                enabled = state.minor > 0 && state.categoryId != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(saveLabel) }
        }
    }

    if (showMore) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            confirmButton = { TextButton({ showMore = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.all_categories)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { c -> CategoryChip(c, c.id == state.categoryId, { showMore = false; choose(c) }) }
                }
            },
        )
    }
    if (showCurrency) {
        CurrencyPickerDialog(selected = state.currency, onDismiss = { showCurrency = false }) {
            state.currencyTouched = true
            state.changeCurrency(it)
            showCurrency = false
        }
    }
    if (showDate) {
        DatePickerSheet(state.at, onDismiss = { showDate = false }) { state.at = it; showDate = false }
    }
}

@Composable
private fun dateLabel(at: Instant): String {
    val d = at.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(current: Instant, onDismiss: () -> Unit, onPick: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val state = rememberDatePickerState(initialSelectedDateMillis = current.atZone(zone).toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    val time = current.atZone(zone).toLocalTime()
                    val future = picked.isAfter(LocalDate.now())
                    onPick(if (future) Instant.now() else picked.atTime(time).atZone(zone).toInstant())
                } else onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

val CommonCurrencies = listOf(
    "INR", "USD", "EUR", "GBP", "AED", "SAR", "SGD", "AUD", "CAD", "JPY", "CNY", "CHF", "NZD", "ZAR", "THB", "MYR", "IDR", "BDT", "LKR", "NPR", "PKR",
)

@Composable
fun CurrencyPickerDialog(selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.pick_currency)) },
        text = {
            LazyColumn {
                items(CommonCurrencies) { code ->
                    val label = remember(code) {
                        runCatching { java.util.Currency.getInstance(code).let { "${it.currencyCode} · ${it.getSymbol(java.util.Locale.getDefault())} · ${it.displayName}" } }.getOrDefault(code)
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (code == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(code) }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                    )
                }
            }
        },
    )
}
