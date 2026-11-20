package com.meetsoneji.tapspend.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.capture.HistoryImporter
import com.meetsoneji.tapspend.capture.ReminderScheduler
import com.meetsoneji.tapspend.container
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.theme.LocalLedger
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Each capture source with its state and the one action that changes it. Re-checked whenever the screen resumes. */
@Composable
fun CaptureSetup(settings: UserSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    var importing by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<Int?>(null) }
    var pickTime by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION") tick
    val sms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val listener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val notify = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun runImport() {
        importing = true
        scope.launch {
            imported = HistoryImporter.import(context)
            c.settings.setHistoryImported(true)
            importing = false
        }
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r[Manifest.permission.READ_SMS] == true && !settings.historyImported) runImport()
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SourceRow(
            title = stringResource(R.string.cap_sms_title), body = stringResource(R.string.cap_sms_body), on = sms,
            action = stringResource(R.string.cap_allow),
        ) { smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }
        if (sms) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        importing -> stringResource(R.string.cap_importing)
                        imported != null -> context.resources.getQuantityString(R.plurals.cap_imported, imported!!, imported!!)
                        else -> stringResource(R.string.cap_import_hint)
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                if (!importing) TextButton(::runImport) { Text(stringResource(R.string.cap_import)) }
            }
        }
        SourceRow(
            title = stringResource(R.string.cap_apps_title), body = stringResource(R.string.cap_apps_body), on = listener,
            action = stringResource(R.string.cap_open_settings),
        ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        SourceRow(
            title = stringResource(R.string.cap_notify_title), body = stringResource(R.string.cap_notify_body), on = notify,
            action = stringResource(R.string.cap_allow),
        ) { if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cap_reminder_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (settings.reminderEnabled) stringResource(R.string.cap_reminder_at, LocalTime.of(settings.reminderMinutes / 60, settings.reminderMinutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                    else stringResource(R.string.cap_reminder_off),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.reminderEnabled) TextButton({ pickTime = true }) { Text(stringResource(R.string.cap_change_time)) }
            Switch(settings.reminderEnabled, { on ->
                scope.launch { c.settings.setReminder(on, settings.reminderMinutes); ReminderScheduler.schedule(context, on, settings.reminderMinutes) }
            })
        }
    }

    if (pickTime) ReminderTimeDialog(settings.reminderMinutes, { pickTime = false }) { m ->
        pickTime = false
        scope.launch { c.settings.setReminder(true, m); ReminderScheduler.schedule(context, true, m) }
    }
}

@Composable
private fun SourceRow(title: String, body: String, on: Boolean, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (on) Text(stringResource(R.string.cap_on), style = MaterialTheme.typography.labelLarge, color = LocalLedger.current.green, modifier = Modifier.padding(horizontal = 12.dp))
        else OutlinedButton(onAction) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val st = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cap_reminder_title)) },
        text = { TimePicker(st) },
        confirmButton = { TextButton({ onPick(st.hour * 60 + st.minute) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
