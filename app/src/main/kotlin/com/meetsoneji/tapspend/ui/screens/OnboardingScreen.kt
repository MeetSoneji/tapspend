package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.entry.CurrencyPickerDialog
import com.meetsoneji.tapspend.ui.requestAddTile
import com.meetsoneji.tapspend.ui.requestPinWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = c.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun saveStep1(name: String, currency: String) = c.appScope.launch { c.settings.setUserName(name); c.settings.setCurrency(currency) }
    fun saveBudget(minor: Long?) = c.appScope.launch { c.settings.setBudget(minor) }
    fun finish() = c.appScope.launch { c.settings.setOnboardingDone(true); c.widgetUpdater.requestUpdate() }
}

/** Three steps, first run only: who are you + currency, budget (skippable), and how to log in under 3 seconds. */
@Composable
fun OnboardingScreen() {
    val vm = appViewModel { OnboardingViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(settings.currency) }
    var budget by rememberSaveable { mutableStateOf("") }
    var pickCurrency by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
        com.meetsoneji.tapspend.ui.theme.BrandMark(72.dp)
        when (step) {
            0 -> {
                Text(stringResource(R.string.onb1_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb1_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.onb1_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.onb1_currency), modifier = Modifier.weight(1f))
                    AssistChip(onClick = { pickCurrency = true }, label = { Text("$currency  ${Money.symbol(currency)}") })
                }
            }
            1 -> {
                Text(stringResource(R.string.onb2_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb2_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    budget, { t -> budget = t.filter { it.isDigit() || it == '.' }.take(14) }, singleLine = true, prefix = { Text(Money.symbol(currency)) },
                    label = { Text(stringResource(R.string.onb2_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                Text(stringResource(R.string.onb3_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb3_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ if (!requestPinWidget(context)) Toast.makeText(context, R.string.msg_widget_manual, Toast.LENGTH_LONG).show() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.set_add_widget)) }
                OutlinedButton({ if (!requestAddTile(context)) Toast.makeText(context, R.string.msg_tile_manual, Toast.LENGTH_LONG).show() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.set_add_tile)) }
                Text(stringResource(R.string.quick_tap_tip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton({ step-- }) { Text(stringResource(R.string.back)) } else Spacer(Modifier.height(1.dp))
            Row {
                if (step == 1) TextButton({ vm.saveBudget(null); step++ }) { Text(stringResource(R.string.skip)) }
                Button(
                    onClick = {
                        when (step) {
                            0 -> { vm.saveStep1(name, currency); step = 1 }
                            1 -> {
                                val minor = runCatching { Money.parseMinor(budget, currency) }.getOrNull()?.takeIf { it in 1..Money.MAX_MINOR }
                                vm.saveBudget(minor); step = 2
                            }
                            else -> vm.finish()
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(if (step == 2) R.string.onb_start else R.string.next)) }
            }
        }
    }
    if (pickCurrency) CurrencyPickerDialog(currency, { pickCurrency = false }) { currency = it; pickCurrency = false }
}
