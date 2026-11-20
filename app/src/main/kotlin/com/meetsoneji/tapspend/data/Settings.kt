package com.meetsoneji.tapspend.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Every field here is surfaced in Settings (layer 3 of the configuration model). */
data class UserSettings(
    val userName: String = "",
    val currency: String = defaultCurrency(),
    val monthlyBudgetMinor: Long? = null,
    val monthStartDay: Int = 1,
    val saveOnCategoryTap: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val appLockEnabled: Boolean = false,
    val hideAmountsOnWidget: Boolean = false,
    val secureScreen: Boolean = false,
    val onboardingDone: Boolean = false,
    /** Daily nudge for spends still waiting for review. Minutes after midnight (default 9 pm). */
    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 21 * 60,
    val historyImported: Boolean = false,
) {
    companion object {
        /** Device locale's currency; falls back to INR . */
        fun defaultCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("INR")
    }
}

private fun defaultCurrency() = UserSettings.defaultCurrency()

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val userName = stringPreferencesKey("user_name")
        val currency = stringPreferencesKey("currency")
        val budget = longPreferencesKey("monthly_budget_minor")
        val startDay = intPreferencesKey("month_start_day")
        val saveOnTap = booleanPreferencesKey("save_on_category_tap")
        val haptics = booleanPreferencesKey("haptics")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val appLock = booleanPreferencesKey("app_lock")
        val hideWidget = booleanPreferencesKey("hide_amounts_widget")
        val secure = booleanPreferencesKey("secure_screen")
        val onboarded = booleanPreferencesKey("onboarding_done")
        val reminderOn = booleanPreferencesKey("reminder_on")
        val reminderAt = intPreferencesKey("reminder_minutes")
        val imported = booleanPreferencesKey("history_imported")
    }

    val flow: Flow<UserSettings> = context.settingsStore.data.map { p ->
        val d = UserSettings()
        UserSettings(
            userName = p[K.userName] ?: d.userName,
            currency = p[K.currency] ?: d.currency,
            monthlyBudgetMinor = null, // budgets were removed in 1.0.1; old values are ignored
            monthStartDay = (p[K.startDay] ?: d.monthStartDay).coerceIn(1, 28),
            saveOnCategoryTap = p[K.saveOnTap] ?: d.saveOnCategoryTap,
            hapticsEnabled = p[K.haptics] ?: d.hapticsEnabled,
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "") }.getOrDefault(d.theme),
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            appLockEnabled = false, // app lock was removed in 1.0.1; an old 'on' value must never lock anyone out
            hideAmountsOnWidget = p[K.hideWidget] ?: d.hideAmountsOnWidget,
            secureScreen = p[K.secure] ?: d.secureScreen,
            onboardingDone = p[K.onboarded] ?: d.onboardingDone,
            reminderEnabled = p[K.reminderOn] ?: d.reminderEnabled,
            reminderMinutes = (p[K.reminderAt] ?: d.reminderMinutes).coerceIn(0, 24 * 60 - 1),
            historyImported = p[K.imported] ?: d.historyImported,
        )
    }

    suspend fun setUserName(v: String) = edit { it[K.userName] = v.trim().take(30) }
    suspend fun setCurrency(v: String) = edit { it[K.currency] = v }
    suspend fun setBudget(minor: Long?) = edit { if (minor == null || minor <= 0) it.remove(K.budget) else it[K.budget] = minor }
    suspend fun setMonthStartDay(v: Int) = edit { it[K.startDay] = v.coerceIn(1, 28) }
    suspend fun setSaveOnCategoryTap(v: Boolean) = edit { it[K.saveOnTap] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[K.haptics] = v }
    suspend fun setTheme(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setDynamicColor(v: Boolean) = edit { it[K.dynamic] = v }
    suspend fun setAppLock(v: Boolean) = edit { it[K.appLock] = v }
    suspend fun setHideAmountsOnWidget(v: Boolean) = edit { it[K.hideWidget] = v }
    suspend fun setSecureScreen(v: Boolean) = edit { it[K.secure] = v }
    suspend fun setOnboardingDone(v: Boolean) = edit { it[K.onboarded] = v }
    suspend fun setReminder(enabled: Boolean, minutes: Int) = edit { it[K.reminderOn] = enabled; it[K.reminderAt] = minutes.coerceIn(0, 24 * 60 - 1) }
    suspend fun setHistoryImported(v: Boolean) = edit { it[K.imported] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }
}
