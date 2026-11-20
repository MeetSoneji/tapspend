package com.meetsoneji.tapspend

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.ui.HapticKind
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.entry.EntryForm
import com.meetsoneji.tapspend.ui.entry.EntryState
import com.meetsoneji.tapspend.ui.haptic
import com.meetsoneji.tapspend.ui.rememberAnimationsEnabled
import com.meetsoneji.tapspend.ui.theme.TapspendTheme
import com.meetsoneji.tapspend.ui.theme.LocalHapticsEnabled
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Non-exported, lightweight, translucent. Every entry point (widget, tile, shortcut, FAB) lands here through an
 * explicit intent. The save runs in the application scope, so swiping the sheet away never loses an expense.
 */
class QuickLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Validate extras: unknown sources are ignored; the category id is checked against the DB in the ViewModel.
        // Unknown sources are ignored (EXTRA_SOURCE is informational only; no usage counters are kept).
        val requestedCategory = intent.getStringExtra(EXTRA_CATEGORY_ID)?.takeIf { it.length in 1..64 }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = QuickLogViewModel(applicationContext.container, requestedCategory) as T
        }
        setContent {
            val vm: QuickLogViewModel = viewModel(factory = factory)
            val settings = vm.settings
            TapspendTheme(
                mode = settings?.theme ?: com.meetsoneji.tapspend.data.ThemeMode.SYSTEM,
                dynamicColor = settings?.dynamicColor ?: false,
                hapticsEnabled = settings?.hapticsEnabled ?: true,
            ) {
                QuickLogSheet(vm, onFinish = { finish() })
            }
        }
        lifecycleScope.launch {
            if (applicationContext.container.settings.flow.first().secureScreen) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_SOURCE = "source"
        const val SOURCE_WIDGET = "widget"
        const val SOURCE_TILE = "tile"
        const val SOURCE_SHORTCUT = "shortcut"
        const val SOURCE_APP = "app"
    }
}

class QuickLogViewModel(private val c: AppContainer, requestedCategory: String?) : ViewModel() {
    val entry = EntryState(currency = UserSettings.defaultCurrency(), category = requestedCategory)
    var settings by mutableStateOf<UserSettings?>(null)
        private set
    var saved by mutableStateOf<ExpenseEntity?>(null)
        private set
    private var saving = false
    val categories = c.categoryCache

    init {
        viewModelScope.launch {
            val s = c.settings.flow.first()
            settings = s
            if (!entry.currencyTouched) entry.changeCurrency(s.currency)
        }
        viewModelScope.launch {
            // Never trust intent data: drop a pre-selected category id that is not a live category.
            val ids = c.repository.activeCategories.first().map { it.id }.toSet()
            if (entry.categoryId != null && entry.categoryId !in ids) entry.categoryId = null
        }
    }

    fun save(categoryId: String) {
        val minor = entry.minor
        if (saving || minor <= 0) return
        saving = true
        // ApplicationScope: finishes even if the activity is destroyed right now.
        val job = c.appScope.async {
            c.repository.addExpense(minor, entry.currency, categoryId, entry.note.ifBlank { null }, entry.at)
        }
        viewModelScope.launch {
            runCatching { job.await() }.onSuccess { saved = it }.onFailure { saving = false; entry.shake++ }
        }
    }

    fun undo() {
        val id = saved?.id ?: return
        c.appScope.launch { c.repository.softDelete(id) }
    }
}

@Composable
private fun QuickLogSheet(vm: QuickLogViewModel, onFinish: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val settings = vm.settings
    val animations = rememberAnimationsEnabled()
    var visible by remember { mutableStateOf(!animations) }
    LaunchedEffect(Unit) { visible = true }
    val view = LocalView.current
    val haptics = LocalHapticsEnabled.current

    val saved = vm.saved
    LaunchedEffect(saved) {
        if (saved != null) {
            view.haptic(HapticKind.CONFIRM, haptics)
            delay(1_500)
            onFinish()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFinish),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (animations) slideInVertically(tween(250)) { it / 3 } + fadeIn(tween(150)) else fadeIn(tween(0)),
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(
                    Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    if (saved == null) {
                        EntryForm(
                            state = vm.entry, categories = categories,
                            saveOnTap = settings?.saveOnCategoryTap ?: true,
                            onSave = vm::save,
                        )
                    } else {
                        SavedPanel(saved, categories.firstOrNull { it.id == saved.categoryId }?.let { categoryName(it) }, animations) {
                            vm.undo()
                            onFinish()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPanel(expense: ExpenseEntity, categoryLabel: String?, animations: Boolean, onUndo: () -> Unit) {
    var shown by remember { mutableStateOf(!animations) }
    LaunchedEffect(Unit) { shown = true }
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(visible = shown, enter = if (animations) scaleIn(tween(250)) + fadeIn(tween(250)) else fadeIn(tween(0))) {
            Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
            }
        }
        Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            listOfNotNull(Money.format(expense.amountMinor, expense.currency), categoryLabel).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUndo) { Text(stringResource(R.string.undo)) }
    }
}
