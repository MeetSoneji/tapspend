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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.entry.CurrencyPickerDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = c.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun saveYou(name: String, currency: String) = c.appScope.launch { c.settings.setUserName(name); c.settings.setCurrency(currency) }
    fun finish() = c.appScope.launch { c.settings.setOnboardingDone(true); c.widgetUpdater.requestUpdate() }
}

/** Two steps, first run only: who you are, then how payments get in. */
@Composable
fun OnboardingScreen() {
    val vm = appViewModel { OnboardingViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf(settings.userName) }
    var currency by rememberSaveable { mutableStateOf(settings.currency) }
    var pickCurrency by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(28.dp))
        if (step == 0) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.onb1_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb1_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.onb1_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.onb1_currency), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    OutlinedButton({ pickCurrency = true }) { Text("$currency  ${Money.symbol(currency)}") }
                }
                Button({ vm.saveYou(name, currency); step = 1 }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.next)) }
            }
        } else {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.onb2_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb2_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            CaptureSetup(settings)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 24.dp)) {
                Button(vm::finish, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.onb_start)) }
                TextButton({ step = 0 }) { Text(stringResource(R.string.back)) }
            }
        }
    }
    if (pickCurrency) CurrencyPickerDialog(currency, { pickCurrency = false }) { currency = it; pickCurrency = false }
}
