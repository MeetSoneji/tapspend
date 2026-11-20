package com.meetsoneji.tapspend.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.meetsoneji.tapspend.MainActivity
import com.meetsoneji.tapspend.QuickLogActivity
import com.meetsoneji.tapspend.R
import com.meetsoneji.tapspend.container
import com.meetsoneji.tapspend.core.BudgetPeriod
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.ui.theme.DarkScheme
import com.meetsoneji.tapspend.ui.theme.LightScheme
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val SMALL = DpSize(110.dp, 40.dp)
private val MEDIUM = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(250.dp, 180.dp)

private val WidgetColors = ColorProviders(light = LightScheme, dark = DarkScheme)

data class WidgetCategory(val id: String, val icon: String, val name: String)
data class WidgetRecent(val id: String, val icon: String, val label: String, val amount: String)
data class WidgetSnapshot(
    val todayText: String,
    val monthText: String,
    val budgetFraction: Float?,
    val budgetLine: String?,
    val categories: List<WidgetCategory>,
    val recent: List<WidgetRecent>,
    val hidden: Boolean,
    val toReview: Int,
)

private const val HIDDEN = "••••"

class QuickLogWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // One snapshot read, then render. The widget session never keeps collecting flows.
        val snapshot = loadSnapshot(context)
        provideContent { GlanceTheme(colors = WidgetColors) { Content(context, snapshot) } }
    }

    private suspend fun loadSnapshot(context: Context): WidgetSnapshot {
        val c = context.container
        val settings = c.settings.flow.first()
        val today = LocalDate.now(c.clock)
        val period = BudgetPeriod.containing(today, settings.monthStartDay)
        val cur = settings.currency
        val dao = c.database.expenseDao()
        val month = dao.totalsSnapshot(period.startIso, period.endIso).firstOrNull { it.currency == cur }?.total ?: 0L
        val day = dao.totalsSnapshot(today.toString(), today.toString()).firstOrNull { it.currency == cur }?.total ?: 0L
        val cats = c.database.categoryDao().observeActive().first()
        val byId = cats.associateBy { it.id }
        val budget = settings.monthlyBudgetMinor
        val hide = settings.hideAmountsOnWidget
        fun money(v: Long) = if (hide) HIDDEN else Money.format(v, cur)
        val recent = dao.recentSnapshot(3).map { e ->
            WidgetRecent(
                id = e.id,
                icon = byId[e.categoryId]?.iconKey ?: "🧾",
                label = e.note ?: byId[e.categoryId]?.let { categoryDisplayName(context, it) } ?: "",
                amount = if (hide) HIDDEN else Money.format(e.amountMinor, e.currency),
            )
        }
        return WidgetSnapshot(
            todayText = money(day),
            monthText = money(month),
            budgetFraction = budget?.let { (month.toFloat() / it.toFloat()).coerceIn(0f, 1f) },
            budgetLine = budget?.let {
                if (hide) HIDDEN else context.getString(R.string.widget_budget_line, Money.format(month, cur), Money.format(it, cur))
            },
            categories = cats.take(4).map { WidgetCategory(it.id, it.iconKey, categoryDisplayName(context, it)) },
            recent = recent,
            hidden = hide,
            toReview = dao.pendingCount(),
        )
    }

    private fun categoryDisplayName(context: Context, c: com.meetsoneji.tapspend.data.CategoryEntity): String =
        com.meetsoneji.tapspend.ui.categoryName(context, c)
}

private fun quickLogIntent(context: Context, categoryId: String? = null): Intent =
    Intent(context, QuickLogActivity::class.java).apply {
        putExtra(QuickLogActivity.EXTRA_SOURCE, QuickLogActivity.SOURCE_WIDGET)
        if (categoryId != null) putExtra(QuickLogActivity.EXTRA_CATEGORY_ID, categoryId)
        // A unique data URI keeps each category's PendingIntent distinct.
        data = Uri.parse("tapspend://quicklog/${categoryId ?: "plain"}")
    }

@Composable
private fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val c = GlanceTheme.colors
    val base = GlanceModifier.fillMaxSize().appWidgetBackground().background(c.widgetBackground).cornerRadius(24.dp)
    // Height budgets (dp, inside padding): small <= 34, medium <= 94, large <= 164.
    when {
        size.height >= LARGE.height -> Box(base.padding(8.dp)) { LargeBody(context, s) }
        size.height >= MEDIUM.height -> Box(base.padding(8.dp)) { MediumBody(context, s) }
        else -> Box(base.padding(horizontal = 12.dp, vertical = 6.dp).clickable(actionStartActivity(quickLogIntent(context)))) { SmallBody(context, s) }
    }
}

@Composable
private fun PlusButton(context: Context, dp: Int) {
    Box(
        modifier = GlanceModifier.size(dp.dp).cornerRadius((dp / 2).dp).background(GlanceTheme.colors.primary)
            .clickable(actionStartActivity(quickLogIntent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = (dp * 0.55).sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SmallBody(context: Context, s: WidgetSnapshot) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(context.getString(R.string.widget_today), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            Text(s.todayText, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        PlusButton(context, 32)
    }
}

/** ~90 dp tall: header (36) + budget bar (14) + category buttons (32) + gaps. */
@Composable
private fun MediumBody(context: Context, s: WidgetSnapshot) {
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(context.getString(R.string.widget_today), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                Text(s.todayText, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            PlusButton(context, 36)
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.budgetFraction != null && s.budgetLine != null) {
                LinearProgressIndicator(
                    progress = s.budgetFraction,
                    modifier = GlanceModifier.defaultWeight().height(5.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.secondaryContainer,
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(s.budgetLine, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
            } else if (s.toReview > 0) {
                Text(
                    context.getString(R.string.widget_to_review, s.toReview),
                    style = TextStyle(color = GlanceTheme.colors.tertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1,
                    modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_REVIEW))),
                )
            } else {
                Text(context.getString(R.string.widget_month_line, s.monthText), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            s.categories.forEachIndexed { i, cat ->
                if (i > 0) Spacer(GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier.defaultWeight().height(32.dp).cornerRadius(12.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .clickable(actionStartActivity(quickLogIntent(context, cat.id))),
                    contentAlignment = Alignment.Center,
                ) { Text(cat.icon, style = TextStyle(fontSize = 17.sp)) }
            }
        }
    }
}

/** Medium + the last 3 expenses (~66 dp more). Tapping a row opens it in Edit. */
@Composable
private fun LargeBody(context: Context, s: WidgetSnapshot) {
    Column(GlanceModifier.fillMaxWidth()) {
        MediumBody(context, s)
        Spacer(GlanceModifier.height(6.dp))
        s.recent.forEach { r ->
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_EDIT
                        putExtra(MainActivity.EXTRA_EXPENSE_ID, r.id)
                        data = Uri.parse("tapspend://edit/${r.id}")
                    })),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.icon, style = TextStyle(fontSize = 14.sp))
                Spacer(GlanceModifier.width(8.dp))
                Text(r.label, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text(r.amount, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

class QuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogWidget()
}
