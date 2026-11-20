package com.meetsoneji.tapspend.ui.screens

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.BuildConfig
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.core.CsvExport
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.data.BackupCodec
import com.meetsoneji.tapspend.data.BackupCrypto
import com.meetsoneji.tapspend.data.BackupData
import com.meetsoneji.tapspend.data.BackupException
import com.meetsoneji.tapspend.data.BackupSettings
import com.meetsoneji.tapspend.data.ImportPreview
import com.meetsoneji.tapspend.data.ThemeMode
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.components.SectionLabel
import com.meetsoneji.tapspend.ui.entry.CurrencyPickerDialog
import com.meetsoneji.tapspend.ui.requestAddTile
import com.meetsoneji.tapspend.ui.requestPinWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = c.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val message = MutableStateFlow<Int?>(null)
    val preview = MutableStateFlow<ImportPreview?>(null)
    val needPassword = MutableStateFlow(false)
    private var pendingBytes: ByteArray? = null
    private var pendingData: BackupData? = null

    private fun set(block: suspend () -> Unit) { c.appScope.launch { block(); c.widgetUpdater.requestUpdate() } }
    fun setName(v: String) = set { c.settings.setUserName(v) }
    fun setCurrency(v: String) = set { c.settings.setCurrency(v) }
    fun setBudget(minor: Long?) = set { c.settings.setBudget(minor) }
    fun setStartDay(v: Int) = set { c.settings.setMonthStartDay(v) }
    fun setSaveOnTap(v: Boolean) = set { c.settings.setSaveOnCategoryTap(v) }
    fun setHaptics(v: Boolean) = set { c.settings.setHaptics(v) }
    fun setTheme(v: ThemeMode) = set { c.settings.setTheme(v) }
    fun setDynamic(v: Boolean) = set { c.settings.setDynamicColor(v) }
    fun setHideWidget(v: Boolean) = set { c.settings.setHideAmountsOnWidget(v) }
    fun setSecure(v: Boolean) = set { c.settings.setSecureScreen(v) }

    // ---- export ----
    fun exportCsv(uri: Uri) = c.appScope.launch {
        runCatching {
            val (cats, expenses) = c.repository.exportAll()
            val byId = cats.associateBy { it.id }
            val sb = StringBuilder(CsvExport.row(listOf("Date", "Time", "Amount", "Currency", "Category", "Note"))).append("\r\n")
            val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            expenses.sortedBy { it.occurredAt }.forEach { e ->
                val zone = runCatching { ZoneId.of(e.zoneId) }.getOrDefault(ZoneId.systemDefault())
                val at = Instant.ofEpochMilli(e.occurredAt).atZone(zone)
                val amount = BigDecimal.valueOf(e.amountMinor).movePointLeft(Money.fractionDigits(e.currency)).toPlainString()
                val cat = byId[e.categoryId]?.let { categoryName(c.appContext, it) } ?: ""
                sb.append(CsvExport.row(listOf(at.format(dateFmt), at.format(timeFmt), amount, e.currency, cat, e.note ?: ""))).append("\r\n")
            }
            write(uri, sb.toString().toByteArray(Charsets.UTF_8))
        }.onSuccess { message.value = R.string.msg_export_ok }.onFailure { message.value = R.string.msg_export_failed }
    }

    fun exportBackup(uri: Uri, password: CharArray?) = c.appScope.launch {
        runCatching {
            val (cats, expenses) = c.repository.exportAll()
            val s = c.settings.flow.first()
            val json = BackupCodec.encode(
                BackupData(cats, expenses, BackupSettings(s.userName, s.currency, s.monthlyBudgetMinor, s.monthStartDay)),
                BuildConfig.VERSION_NAME, System.currentTimeMillis(),
            ).toByteArray(Charsets.UTF_8)
            val out = if (password != null && password.isNotEmpty()) BackupCrypto.encrypt(json, password) else json
            write(uri, out)
        }.onSuccess { message.value = R.string.msg_export_ok }.onFailure { message.value = R.string.msg_export_failed }
    }

    private suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = c.appContext.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
        out.use { it.write(bytes) }
    }

    // ---- import ----
    fun readImport(uri: Uri?, password: CharArray?) = viewModelScope.launch {
        val bytes = if (uri != null) {
            runCatching { withContext(Dispatchers.IO) { readLimited(uri) } }.getOrElse { e ->
                message.value = if (e is BackupException) reasonMessage(e.reason) else R.string.msg_import_unreadable
                return@launch
            }.also { pendingBytes = it }
        } else pendingBytes ?: return@launch
        val result = withContext(Dispatchers.Default) { runCatching { BackupCrypto.open(bytes, password) } }
        result.onSuccess { data ->
            needPassword.value = false
            pendingData = data
            preview.value = c.repository.previewImport(data)
        }.onFailure { e ->
            if (e is BackupException && e.reason == BackupException.Reason.PASSWORD_REQUIRED) needPassword.value = true
            else {
                if (e is BackupException && e.reason == BackupException.Reason.WRONG_PASSWORD) needPassword.value = false
                message.value = if (e is BackupException) reasonMessage(e.reason) else R.string.msg_import_corrupt
                if (!(e is BackupException && e.reason == BackupException.Reason.WRONG_PASSWORD)) pendingBytes = null
            }
        }
    }

    fun confirmImport() {
        val data = pendingData ?: return
        preview.value = null
        c.appScope.launch {
            val wasEmpty = c.repository.activeExpenseCount() == 0
            runCatching { c.repository.importData(data) }
                .onSuccess {
                    // A fresh install restoring a backup also gets the saved name / currency / budget back.
                    if (wasEmpty) data.settings?.let { s ->
                        c.settings.setUserName(s.userName); c.settings.setCurrency(s.currency)
                        c.settings.setBudget(s.monthlyBudgetMinor); c.settings.setMonthStartDay(s.monthStartDay)
                    }
                    message.value = R.string.msg_import_ok
                }
                .onFailure { message.value = R.string.msg_import_corrupt }
            clearPending()
        }
    }

    fun cancelImport() { preview.value = null; needPassword.value = false; clearPending() }
    private fun clearPending() { pendingBytes = null; pendingData = null }
    fun messageShown() { message.value = null }

    private fun reasonMessage(r: BackupException.Reason): Int = when (r) {
        BackupException.Reason.TOO_LARGE -> R.string.msg_import_too_large
        BackupException.Reason.NOT_A_BACKUP -> R.string.msg_import_not_backup
        BackupException.Reason.WRONG_VERSION -> R.string.msg_import_wrong_version
        BackupException.Reason.WRONG_PASSWORD -> R.string.msg_import_wrong_password
        BackupException.Reason.PASSWORD_REQUIRED -> R.string.msg_import_wrong_password
        BackupException.Reason.CORRUPT -> R.string.msg_import_corrupt
    }

    private fun readLimited(uri: Uri): ByteArray {
        val ins = c.appContext.contentResolver.openInputStream(uri) ?: throw BackupException(BackupException.Reason.CORRUPT, "no stream")
        ins.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > BackupCodec.MAX_BYTES + 64) throw BackupException(BackupException.Reason.TOO_LARGE, "too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}

@Composable
fun SettingsScreen(onCategories: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val needPassword by vm.needPassword.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = s ?: return

    var dialog by remember { mutableStateOf<String?>(null) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }

    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, context.getString(it), Toast.LENGTH_LONG).show(); vm.messageShown() }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCsv) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.exportBackup(it, exportPassword) }
        exportPassword = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.readImport(it, null) } }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))

        SectionLabel(stringResource(R.string.set_general))
        Row2(R.string.set_name, settings.userName.ifBlank { stringResource(R.string.set_name_empty) }) { dialog = "name" }
        Row2(R.string.set_currency, settings.currency) { dialog = "currency" }

        SectionLabel(stringResource(R.string.set_month))
        Row2(R.string.set_month_start, stringResource(R.string.set_month_start_value, settings.monthStartDay)) { dialog = "startday" }

        SectionLabel(stringResource(R.string.categories_title))
        Row2(R.string.set_manage_categories, stringResource(R.string.set_manage_categories_sub), onClick = onCategories)

        SectionLabel(stringResource(R.string.set_appearance))
        Row2(R.string.set_theme, stringResource(when (settings.theme) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark })) { dialog = "theme" }
        if (Build.VERSION.SDK_INT >= 31) SwitchRow(R.string.set_dynamic, R.string.set_dynamic_sub, settings.dynamicColor, vm::setDynamic)
        SwitchRow(R.string.set_haptics, R.string.set_haptics_sub, settings.hapticsEnabled, vm::setHaptics)

        SectionLabel(stringResource(R.string.set_logging))
        SwitchRow(R.string.set_save_on_tap, R.string.set_save_on_tap_sub, settings.saveOnCategoryTap, vm::setSaveOnTap)
        Row2(R.string.set_add_widget, stringResource(R.string.set_add_widget_sub)) {
            if (!requestPinWidget(context)) Toast.makeText(context, R.string.msg_widget_manual, Toast.LENGTH_LONG).show()
        }
        Row2(R.string.set_add_tile, stringResource(R.string.set_add_tile_sub)) {
            if (!requestAddTile(context)) Toast.makeText(context, R.string.msg_tile_manual, Toast.LENGTH_LONG).show()
        }
        Text(stringResource(R.string.quick_tap_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        SectionLabel(stringResource(R.string.set_privacy))
        SwitchRow(R.string.set_hide_widget, R.string.set_hide_widget_sub, settings.hideAmountsOnWidget, vm::setHideWidget)
        SwitchRow(R.string.set_secure, R.string.set_secure_sub, settings.secureScreen, vm::setSecure)

        SectionLabel(stringResource(R.string.set_backup))
        Row2(R.string.set_export_csv, stringResource(R.string.set_export_csv_sub)) { csvLauncher.launch("tapspend-${java.time.LocalDate.now()}.csv") }
        Row2(R.string.set_export_backup, stringResource(R.string.set_export_backup_sub)) { dialog = "exportpw" }
        Row2(R.string.set_import_backup, stringResource(R.string.set_import_backup_sub)) { importLauncher.launch(arrayOf("*/*")) }

        SectionLabel(stringResource(R.string.set_about))
        Row2(R.string.set_version, BuildConfig.VERSION_NAME, onClick = {})
        Row2(R.string.set_privacy_policy, stringResource(R.string.set_privacy_policy_sub)) { dialog = "privacy" }
        Row2(R.string.set_licences, stringResource(R.string.set_licences_sub)) { dialog = "licences" }
        Text(stringResource(R.string.about_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }

    when (dialog) {
        "name" -> TextInputDialog(R.string.set_name, settings.userName, KeyboardType.Text, onDismiss = { dialog = null }) { vm.setName(it); dialog = null }
        "currency" -> CurrencyPickerDialog(settings.currency, { dialog = null }) { vm.setCurrency(it); dialog = null }
        "startday" -> StartDayDialog(settings.monthStartDay, { dialog = null }) { vm.setStartDay(it); dialog = null }
        "theme" -> ThemeDialog(settings.theme, { dialog = null }) { vm.setTheme(it); dialog = null }
        "exportpw" -> PasswordDialog(R.string.export_pw_title, R.string.export_pw_body, allowEmpty = true, onDismiss = { dialog = null }) { pw ->
            exportPassword = pw
            dialog = null
            backupLauncher.launch("tapspend-backup-${java.time.LocalDate.now()}.tsb")
        }
        "privacy" -> InfoDialog(R.string.set_privacy_policy, R.string.privacy_text) { dialog = null }
        "licences" -> InfoDialog(R.string.set_licences, R.string.licences_text) { dialog = null }
    }
    if (needPassword) {
        PasswordDialog(R.string.import_pw_title, R.string.import_pw_body, allowEmpty = false, onDismiss = vm::cancelImport) { pw -> vm.readImport(null, pw) }
    }
    preview?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text(stringResource(R.string.import_preview_title)) },
            text = { Text(stringResource(R.string.import_preview_body, p.newExpenses, p.totalExpenses, p.newCategories, p.totalCategories)) },
            confirmButton = { TextButton(vm::confirmImport) { Text(stringResource(R.string.import_confirm)) } },
            dismissButton = { TextButton(vm::cancelImport) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Row2(@StringRes title: Int, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes subtitle: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TextInputDialog(@StringRes title: Int, initial: String, keyboard: KeyboardType, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(title)) },
        text = { OutlinedTextField(text, { text = it.take(30) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard)) },
        confirmButton = { TextButton({ onOk(text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun BudgetDialog(settings: UserSettings, onDismiss: () -> Unit, onOk: (Long?) -> Unit) {
    val fd = Money.fractionDigits(settings.currency)
    var text by remember {
        mutableStateOf(settings.monthlyBudgetMinor?.let { BigDecimal.valueOf(it).movePointLeft(fd).stripTrailingZeros().toPlainString() } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.set_budget_amount)) },
        text = {
            Column {
                OutlinedTextField(
                    text, { t -> text = t.filter { it.isDigit() || it == '.' }.take(14) }, singleLine = true,
                    prefix = { Text(Money.symbol(settings.currency)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(stringResource(R.string.budget_dialog_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton({
                if (text.isBlank()) onOk(null)
                else runCatching { Money.parseMinor(text, settings.currency) }.onSuccess { if (it in 1..Money.MAX_MINOR) onOk(it) }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StartDayDialog(current: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var v by remember { mutableFloatStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.set_month_start)) },
        text = {
            Column {
                Text(stringResource(R.string.start_day_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.set_month_start_value, v.toInt()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(v, { v = it }, valueRange = 1f..28f, steps = 26)
            }
        },
        confirmButton = { TextButton({ onOk(v.toInt()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ThemeDialog(current: ThemeMode, onDismiss: () -> Unit, onPick: (ThemeMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.set_theme)) },
        text = {
            Column {
                listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(mode) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == current, onClick = { onPick(mode) })
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun PasswordDialog(@StringRes title: Int, @StringRes body: Int, allowEmpty: Boolean, onDismiss: () -> Unit, onOk: (CharArray?) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    pw, { pw = it.take(128) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton({ onOk(if (pw.isEmpty()) null else pw.toCharArray()) }, enabled = allowEmpty || pw.isNotEmpty()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun InfoDialog(@StringRes title: Int, @StringRes body: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(title)) },
        text = { Text(stringResource(body), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
