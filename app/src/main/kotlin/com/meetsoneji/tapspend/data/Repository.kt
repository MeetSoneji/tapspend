package com.meetsoneji.tapspend.data

import androidx.room.withTransaction
import com.meetsoneji.tapspend.core.Money
import com.meetsoneji.tapspend.core.Period
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class InvalidExpenseException(message: String) : IllegalArgumentException(message)

data class ImportPreview(val newExpenses: Int, val newCategories: Int, val totalExpenses: Int, val totalCategories: Int)

/** All reads are SQL aggregates (SUM ... GROUP BY); nothing loads every row to compute a total. */
class ExpenseRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val onChanged: () -> Unit,
) {
    private val expenses = db.expenseDao()
    private val categories = db.categoryDao()

    val activeCategories: Flow<List<CategoryEntity>> = categories.observeActive()
    val allCategories: Flow<List<CategoryEntity>> = categories.observeAll()

    fun observeTotals(p: Period) = expenses.observeTotals(p.startIso, p.endIso)
    fun observeCategoryTotals(p: Period) = expenses.observeCategoryTotals(p.startIso, p.endIso)
    fun observeDailyTotals(p: Period) = expenses.observeDailyTotals(p.startIso, p.endIso)
    fun observeToday() = expenses.observeDayTotal(LocalDate.now(clock).toString())
    fun observeRecent(limit: Int) = expenses.observeRecent(limit)
    fun observeExpense(id: String) = expenses.observe(id)
    fun observeHistory(p: Period, categoryId: String?, query: String?): Flow<List<ExpenseEntity>> {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }?.replace("\\", "\\\\")?.replace("%", "\\%")?.replace("_", "\\_")
        return expenses.observeHistory(p.startIso, p.endIso, categoryId, q)
    }

    suspend fun addExpense(
        amountMinor: Long,
        currency: String,
        categoryId: String,
        note: String?,
        at: Instant = clock.instant(),
        zone: ZoneId = clock.zone,
    ): ExpenseEntity {
        validate(amountMinor, currency, note)
        val now = clock.millis()
        val entity = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            amountMinor = amountMinor,
            currency = currency,
            categoryId = categoryId,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            occurredAt = at.toEpochMilli(),
            zoneId = zone.id,
            localDate = at.atZone(zone).toLocalDate().toString(),
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            expenses.purgeDeletedBefore(now - UNDO_WINDOW_MS)
            expenses.upsert(entity)
            categories.touch(categoryId, now)
        }
        onChanged()
        return entity
    }

    suspend fun updateExpense(updated: ExpenseEntity) {
        validate(updated.amountMinor, updated.currency, updated.note)
        val now = clock.millis()
        expenses.upsert(updated.copy(note = updated.note?.trim()?.takeIf { it.isNotEmpty() }, updatedAt = now))
        onChanged()
    }

    suspend fun getExpense(id: String) = expenses.get(id)

    suspend fun softDelete(id: String) {
        expenses.softDelete(id, clock.millis())
        onChanged()
    }

    suspend fun undoDelete(id: String) {
        expenses.undoDelete(id)
        onChanged()
    }

    suspend fun categoryExists(id: String) = categories.get(id) != null

    // ---- categories ----
    suspend fun addCategory(name: String, icon: String, colorKey: String): CategoryEntity {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) throw InvalidExpenseException("Name is empty")
        val c = CategoryEntity(
            id = UUID.randomUUID().toString(), name = clean, presetKey = null, iconKey = icon, colorKey = colorKey,
            sortOrder = categories.maxSortOrder() + 1,
        )
        categories.upsert(c)
        onChanged()
        return c
    }

    suspend fun updateCategory(c: CategoryEntity) {
        categories.upsert(c.copy(name = c.name.trim().take(40).ifEmpty { c.name }))
        onChanged()
    }

    /** Swaps two categories' positions. Categories are never hard-deleted once created (archive instead). */
    suspend fun reorder(ordered: List<CategoryEntity>) {
        db.withTransaction { ordered.forEachIndexed { i, c -> if (c.sortOrder != i) categories.upsert(c.copy(sortOrder = i)) } }
        onChanged()
    }

    // ---- backup ----
    suspend fun exportAll(): Pair<List<CategoryEntity>, List<ExpenseEntity>> = categories.all() to expenses.allActive()

    suspend fun previewImport(data: BackupData): ImportPreview {
        val existingE = expenses.ids().toSet()
        val existingC = categories.ids().toSet()
        return ImportPreview(
            newExpenses = data.expenses.count { it.id !in existingE },
            newCategories = data.categories.count { it.id !in existingC },
            totalExpenses = data.expenses.size,
            totalCategories = data.categories.size,
        )
    }

    suspend fun activeExpenseCount() = expenses.activeCount()

    /** Merge by ID, all-or-nothing. Categories first so the RESTRICT foreign key is satisfied. */
    suspend fun importData(data: BackupData) {
        db.withTransaction {
            categories.upsertAll(data.categories)
            expenses.upsertAll(data.expenses)
        }
        onChanged()
    }

    private fun validate(amountMinor: Long, currency: String, note: String?) {
        if (amountMinor <= 0) throw InvalidExpenseException("Amount must be greater than zero")
        if (amountMinor > Money.MAX_MINOR) throw InvalidExpenseException("Amount is too large")
        if (!Money.isValidCode(currency)) throw InvalidExpenseException("Invalid currency")
        if (note != null && note.length > 200) throw InvalidExpenseException("Note is too long")
    }

    companion object {
        const val UNDO_WINDOW_MS = 30_000L
    }
}
