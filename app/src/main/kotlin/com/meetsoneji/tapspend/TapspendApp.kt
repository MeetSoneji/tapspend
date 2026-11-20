package com.meetsoneji.tapspend

import android.app.Application
import android.content.Context
import com.meetsoneji.tapspend.data.AppDatabase
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.ExpenseRepository
import com.meetsoneji.tapspend.data.SettingsRepository
import com.meetsoneji.tapspend.widget.MidnightScheduler
import com.meetsoneji.tapspend.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/** Hand-rolled service locator: small, no code generation, and everything is lazy so cold start stays cheap. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Writes that must survive the UI closing (Quick Log save, import) run here, never in viewModelScope. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.systemDefaultZone()

    val settings by lazy { SettingsRepository(appContext) }
    val database by lazy { AppDatabase.build(appContext) }
    val widgetUpdater by lazy { WidgetUpdater(appContext, appScope) }
    val repository by lazy { ExpenseRepository(database, clock) { widgetUpdater.requestUpdate() } }

    /**
     * In-memory category cache so Quick Log's first frame never waits on disk. Starts empty on a cold
     * process and is replaced by real DB values as soon as they arrive.
     */
    val categoryCache: StateFlow<List<CategoryEntity>> by lazy {
        repository.activeCategories.stateIn(appScope, SharingStarted.Eagerly, emptyList())
    }
}

class TapspendApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.categoryCache // warm lazily on a background thread
        MidnightScheduler.schedule(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as TapspendApp).container
