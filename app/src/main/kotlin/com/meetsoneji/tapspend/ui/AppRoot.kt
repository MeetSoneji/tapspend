package com.meetsoneji.tapspend.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.QuickLogActivity
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.container
import com.meetsoneji.tapspend.data.UserSettings
import com.meetsoneji.tapspend.ui.screens.CategoriesScreen
import com.meetsoneji.tapspend.ui.screens.EditScreen
import com.meetsoneji.tapspend.ui.screens.HistoryScreen
import com.meetsoneji.tapspend.ui.screens.InboxScreen
import com.meetsoneji.tapspend.ui.screens.InsightsScreen
import com.meetsoneji.tapspend.ui.screens.OnboardingScreen
import com.meetsoneji.tapspend.ui.screens.SettingsScreen
import com.meetsoneji.tapspend.ui.theme.TapspendTheme
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data class HistoryRoute(val categoryId: String? = null)
@Serializable data object InsightsRoute
@Serializable data object SettingsRoute
@Serializable data object CategoriesRoute
@Serializable data class EditRoute(val id: String)

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContext.current.applicationContext.container
    return viewModel(factory = viewModelFactory { initializer { create(c) } })
}

fun quickLogIntent(context: android.content.Context): Intent =
    Intent(context, QuickLogActivity::class.java).putExtra(QuickLogActivity.EXTRA_SOURCE, QuickLogActivity.SOURCE_APP)

@Composable
fun AppRoot(
    settings: UserSettings?,
    pendingEditId: String?,
    onEditHandled: () -> Unit,
    pendingReviewId: String? = null,
    onReviewHandled: () -> Unit = {},
) {
    TapspendTheme(
        mode = settings?.theme ?: com.meetsoneji.tapspend.data.ThemeMode.SYSTEM,
        dynamicColor = settings?.dynamicColor ?: false,
        hapticsEnabled = settings?.hapticsEnabled ?: true,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            when {
                settings == null -> Unit // splash is still showing; nothing composed yet
                !settings.onboardingDone -> OnboardingScreen()
                else -> MainNav(pendingEditId, onEditHandled, pendingReviewId, onReviewHandled)
            }
        }
    }
}

private data class Tab(val route: Any, val icon: @Composable () -> Unit, val label: Int, val matches: (androidx.navigation.NavDestination) -> Boolean)

@Composable
private fun MainNav(pendingEditId: String?, onEditHandled: () -> Unit, pendingReviewId: String?, onReviewHandled: () -> Unit) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(HomeRoute, { androidx.compose.material3.Icon(Icons.Default.Home, null) }, R.string.nav_home) { it.hasRoute<HomeRoute>() },
        Tab(HistoryRoute(), { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.List, null) }, R.string.nav_history) { it.hasRoute<HistoryRoute>() },
        Tab(InsightsRoute, { BarsIcon() }, R.string.nav_insights) { it.hasRoute<InsightsRoute>() },
        Tab(SettingsRoute, { androidx.compose.material3.Icon(Icons.Default.Settings, null) }, R.string.nav_settings) { it.hasRoute<SettingsRoute>() },
    )
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = destination != null && tabs.any { it.matches(destination) }

    LaunchedEffect(pendingReviewId) {
        if (pendingReviewId != null) nav.navigate(HomeRoute) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
    }
    LaunchedEffect(pendingEditId) {
        if (pendingEditId != null) {
            nav.navigate(EditRoute(pendingEditId))
            onEditHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val label = stringResource(tab.label)
                        NavigationBarItem(
                            selected = destination?.let(tab.matches) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh, selectedIconColor = MaterialTheme.colorScheme.onSurface, selectedTextColor = MaterialTheme.colorScheme.onSurface),
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable<HomeRoute> {
                InboxScreen(
                    reviewId = pendingReviewId, onReviewHandled = onReviewHandled,
                    onEdit = { nav.navigate(EditRoute(it)) }, onSeeAll = { nav.navigate(HistoryRoute()) },
                    onSetUpCapture = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
                )
            }
            composable<HistoryRoute> { entry ->
                val route = entry.toRoute<HistoryRoute>()
                HistoryScreen(initialCategoryId = route.categoryId, onEdit = { nav.navigate(EditRoute(it)) })
            }
            composable<InsightsRoute> {
                InsightsScreen(onCategory = { id -> nav.navigate(HistoryRoute(id)) { launchSingleTop = true } })
            }
            composable<SettingsRoute> { SettingsScreen(onCategories = { nav.navigate(CategoriesRoute) }) }
            composable<CategoriesRoute> { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable<EditRoute> { entry ->
                val route = entry.toRoute<EditRoute>()
                EditScreen(route.id, onDone = { nav.popBackStack() })
            }
        }
    }
}

/** Three rounded bars (core icons have no chart glyph); tinted by the current content colour. */
@Composable
private fun BarsIcon() {
    val c = androidx.compose.material3.LocalContentColor.current
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width / 6
        listOf(0.40f to 3, 0.75f to 8, 0.55f to 13).forEachIndexed { i, (h, _) ->
            val x = size.width * (0.16f + i * 0.30f)
            drawRoundRect(c, androidx.compose.ui.geometry.Offset(x, size.height * (0.9f - h * 0.8f)), androidx.compose.ui.geometry.Size(w, size.height * h * 0.8f), androidx.compose.ui.geometry.CornerRadius(w / 2))
        }
    }
}
