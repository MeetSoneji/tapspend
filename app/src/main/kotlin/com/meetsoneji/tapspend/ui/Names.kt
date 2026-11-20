package com.meetsoneji.tapspend.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.data.CategoryEntity

private fun presetRes(key: String?): Int? = when (key) {
    "food" -> R.string.cat_food
    "chai" -> R.string.cat_chai
    "groceries" -> R.string.cat_groceries
    "transport" -> R.string.cat_transport
    "shopping" -> R.string.cat_shopping
    "bills" -> R.string.cat_bills
    "health" -> R.string.cat_health
    "entertainment" -> R.string.cat_entertainment
    "other" -> R.string.cat_other
    else -> null
}

/** Preset categories translate through string resources; user-made ones show their own name. */
fun categoryName(context: Context, c: CategoryEntity): String =
    presetRes(c.presetKey)?.let { context.getString(it) } ?: c.name

@Composable
fun categoryName(c: CategoryEntity): String = categoryName(LocalContext.current, c)
