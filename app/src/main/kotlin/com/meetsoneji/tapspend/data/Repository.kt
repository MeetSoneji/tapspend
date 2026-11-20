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
    fun observeHistory(p: Period, categoryId: String?, query: String?, includeTransfers: Boolean = false): Flow<List<ExpenseEntity>> {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }?.replace("\\", "\\\\")?.replace("%", "\\%")?.replace("_", "\\_")
        return expenses.observeHistory(p.startIso, p.endIso, categoryId, q, includeTransfers)
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

    // ---- review queue ----
    val pending: Flow<List<ExpenseEntity>> = expenses.observePending()
    fun observeCashback(p: Period) = expenses.observeCashback(p.startIso, p.endIso)
    fun observeRefundedFor(id: String) = expenses.observeRefundedFor(id)
    val accounts = db.accountDao().observeAll()

    /** Confirms a detected transaction, optionally with a different category or kind, and remembers the choice. */
    suspend fun approve(id: String, categoryId: String? = null, kind: String? = null) {
        val e = expenses.get(id) ?: return
        val now = clock.millis()
        val updated = e.copy(
            status = TxnStatus.CONFIRMED,
            categoryId = categoryId?.takeIf { categories.get(it) != null } ?: e.categoryId,
            kind = kind ?: e.kind,
            updatedAt = now,
        )
        db.withTransaction {
            expenses.upsert(updated)
            if (updated.kind == "EXPENSE") categories.touch(updated.categoryId, now)
            updated.merchant?.let { m ->
                db.ruleDao().upsert(
                    MerchantRuleEntity(
                        com.meetsoneji.tapspend.core.capture.TxnParser.merchantKey(m), updated.kind,
                        if (updated.kind == "EXPENSE" || updated.kind == "REFUND") updated.categoryId else null, now,
                    ),
                )
            }
        }
        onChanged()
    }

    /** Approves every pending item with its suggested category and kind. */
    suspend fun approveAllPending(): Int {
        val list = expenses.pending()
        list.forEach { approve(it.id) }
        return list.size
    }

    /**
     * Records a refund that arrived without any SMS: a REFUND entry linked to the original, in the same category,
     * dated today, so it reduces this month's spend.
     */
    suspend fun markRefunded(original: ExpenseEntity, amountMinor: Long, at: Instant = clock.instant()) {
        if (amountMinor <= 0 || amountMinor > original.amountMinor) throw InvalidExpenseException("Refund must be between 0 and the original amount")
        val now = clock.millis()
        val zone = clock.zone
        expenses.upsert(
            ExpenseEntity(
                id = UUID.randomUUID().toString(), amountMinor = amountMinor, currency = original.currency,
                categoryId = original.categoryId, note = null, occurredAt = at.toEpochMilli(), zoneId = zone.id,
                localDate = at.atZone(zone).toLocalDate().toString(), createdAt = now, updatedAt = now,
                kind = "REFUND", status = TxnStatus.CONFIRMED, source = TxnSource.MANUAL,
                merchant = original.merchant, accountId = original.accountId, linkedId = original.id,
            ),
        )
        onChanged()
    }

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
