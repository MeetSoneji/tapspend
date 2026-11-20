package com.meetsoneji.tapspend.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.ui.appViewModel
import com.meetsoneji.tapspend.ui.categoryName
import com.meetsoneji.tapspend.ui.theme.CategoryPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> =
        c.repository.allCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, icon: String, color: String) = c.appScope.launch { runCatching { c.repository.addCategory(name, icon, color) } }
    fun update(cat: CategoryEntity) = c.appScope.launch { c.repository.updateCategory(cat) }
    fun move(list: List<CategoryEntity>, from: Int, to: Int) {
        if (to !in list.indices) return
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        c.appScope.launch { c.repository.reorder(m) }
    }
}

private val Emojis = listOf(
    "🍛", "🍕", "🍔", "🍜", "🍎", "☕", "🍺", "🍰", "🛒", "🥦", "🥛", "🚌", "🚕", "🛺", "🚇", "🚗", "⛽", "✈️", "🛍️", "👕",
    "👟", "📱", "💻", "🎧", "💡", "🔌", "💧", "🏠", "🧾", "💊", "🏥", "🦷", "🎬", "🎮", "🎵", "📚", "🎓", "🏋️", "💇", "🎁",
    "🐶", "👶", "💰", "🧳", "🪔", "🙏", "🔧", "🧹", "🌿", "❤️",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val cats by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, stringResource(R.string.category_add)) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding().navigationBarsPadding()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onBack) { Text("← " + stringResource(R.string.back)) }
                        Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(stringResource(R.string.categories_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                itemsIndexed(cats, key = { _, c -> c.id }) { index, c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editing = c }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(CategoryPalette.color(c.colorKey).copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) { Text(c.iconKey, fontSize = 18.sp) }
                        Text(categoryName(c), modifier = Modifier.weight(1f).padding(start = 12.dp), color = if (c.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        TextButton({ vm.move(cats, index, index - 1) }, enabled = index > 0) { Text("▲", modifier = Modifier.semantics { contentDescription = "Move up" }) }
                        TextButton({ vm.move(cats, index, index + 1) }, enabled = index < cats.lastIndex) { Text("▼", modifier = Modifier.semantics { contentDescription = "Move down" }) }
                        Switch(checked = !c.isArchived, onCheckedChange = { vm.update(c.copy(isArchived = !it)) })
                    }
                }
            }
        }
    }

    if (adding) CategoryDialog(null, onDismiss = { adding = false }) { name, icon, color -> vm.add(name, icon, color); adding = false }
    editing?.let { cat ->
        CategoryDialog(cat, onDismiss = { editing = null }) { name, icon, color ->
            vm.update(cat.copy(name = name, iconKey = icon, colorKey = color, presetKey = if (name == cat.name) cat.presetKey else null))
            editing = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(existing: CategoryEntity?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val initialName = existing?.let { categoryName(it) } ?: ""
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(existing?.iconKey ?: Emojis.first()) }
    var color by remember { mutableStateOf(existing?.colorKey ?: "teal") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.category_add else R.string.category_edit)) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                item {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.category_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.category_icon), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Emojis.forEach { e ->
                            Box(
                                Modifier.size(44.dp).then(if (e == icon) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier).clickable { icon = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 22.sp) }
                        }
                    }
                    Text(stringResource(R.string.category_colour), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryPalette.keys.forEach { k ->
                            Box(
                                Modifier.size(40.dp).background(CategoryPalette.color(k), CircleShape)
                                    .then(if (k == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { color = k }.semantics { contentDescription = k },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ if (name.isNotBlank()) onSave(name.trim(), icon, color) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
