package com.meetsoneji.tapspend.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query(
        "SELECT * FROM category WHERE is_archived = 0 " +
            "ORDER BY CASE WHEN last_used_at IS NULL THEN 1 ELSE 0 END, last_used_at DESC, sort_order",
    )
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sort_order")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sort_order")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    // @Upsert (not INSERT OR REPLACE): REPLACE would delete the row and trip the RESTRICT foreign key.
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("UPDATE category SET last_used_at = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM category")
    suspend fun maxSortOrder(): Int

    @Query("SELECT id FROM category")
    suspend fun ids(): List<String>
}

private const val LIVE = "deleted_at IS NULL"
private const val SPEND = "deleted_at IS NULL AND status = 'CONFIRMED' AND kind IN ('EXPENSE','REFUND','CASHBACK')"
private const val NET = "SUM(CASE kind WHEN 'EXPENSE' THEN amount_minor WHEN 'REFUND' THEN -amount_minor WHEN 'CASHBACK' THEN -amount_minor ELSE 0 END)"
private const val NET_NO_CASHBACK = "SUM(CASE kind WHEN 'EXPENSE' THEN amount_minor WHEN 'REFUND' THEN -amount_minor ELSE 0 END)"

@Dao
interface ExpenseDao {
    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Upsert
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT * FROM expense WHERE id = :id AND $LIVE")
    suspend fun get(id: String): ExpenseEntity?

    @Query("SELECT * FROM expense WHERE id = :id AND $LIVE")
    fun observe(id: String): Flow<ExpenseEntity?>

    @Query("UPDATE expense SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE expense SET deleted_at = NULL WHERE id = :id")
    suspend fun undoDelete(id: String)

    @Query("DELETE FROM expense WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    // ---- spend (confirmed only; refunds and cashback reduce it) ----
    @Query("SELECT currency, $NET AS total FROM expense WHERE $SPEND AND local_date BETWEEN :start AND :end GROUP BY currency ORDER BY total DESC")
    fun observeTotals(start: String, end: String): Flow<List<CurrencyTotal>>

    @Query("SELECT category_id AS categoryId, currency, $NET_NO_CASHBACK AS total FROM expense WHERE $SPEND AND kind != 'CASHBACK' AND local_date BETWEEN :start AND :end GROUP BY category_id, currency")
    fun observeCategoryTotals(start: String, end: String): Flow<List<CategoryTotal>>

    @Query("SELECT currency, SUM(amount_minor) AS total FROM expense WHERE $SPEND AND kind = 'CASHBACK' AND local_date BETWEEN :start AND :end GROUP BY currency")
    fun observeCashback(start: String, end: String): Flow<List<CurrencyTotal>>

    @Query("SELECT local_date AS localDate, currency, $NET_NO_CASHBACK AS total FROM expense WHERE $SPEND AND kind != 'CASHBACK' AND local_date BETWEEN :start AND :end GROUP BY local_date, currency")
    fun observeDailyTotals(start: String, end: String): Flow<List<DayTotal>>

    @Query("SELECT currency, $NET AS total FROM expense WHERE $SPEND AND local_date = :date GROUP BY currency")
    fun observeDayTotal(date: String): Flow<List<CurrencyTotal>>

    @Query("SELECT * FROM expense WHERE $SPEND ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expense WHERE $LIVE AND status = 'CONFIRMED' AND local_date BETWEEN :start AND :end " +
            "AND (:includeTransfers = 1 OR kind IN ('EXPENSE','REFUND','CASHBACK')) " +
            "AND (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND (:query IS NULL OR note LIKE '%' || :query || '%' ESCAPE '\\' OR merchant LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY occurred_at DESC",
    )
    fun observeHistory(start: String, end: String, categoryId: String?, query: String?, includeTransfers: Boolean): Flow<List<ExpenseEntity>>

    // ---- review queue ----
    @Query("SELECT * FROM expense WHERE $LIVE AND status = 'PENDING' ORDER BY occurred_at DESC")
    fun observePending(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE $LIVE AND status = 'PENDING' ORDER BY occurred_at DESC")
    suspend fun pending(): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expense WHERE $LIVE AND status = 'PENDING'")
    suspend fun pendingCount(): Int

    /** Possible duplicates of a new alert: same amount within a time window (any source, including manual). */
    @Query("SELECT * FROM expense WHERE $LIVE AND amount_minor = :amount AND occurred_at BETWEEN :from AND :to")
    suspend fun sameAmountBetween(amount: Long, from: Long, to: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE $LIVE AND kind = 'EXPENSE' AND status = 'CONFIRMED' AND merchant = :merchant AND occurred_at < :before ORDER BY occurred_at DESC LIMIT 5")
    suspend fun recentByMerchant(merchant: String, before: Long): List<ExpenseEntity>

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM expense WHERE $LIVE AND kind = 'REFUND' AND linked_id = :id")
    fun observeRefundedFor(id: String): Flow<Long>

    // ---- export / misc ----
    @Query("SELECT * FROM expense WHERE $LIVE ORDER BY occurred_at DESC")
    suspend fun allActive(): List<ExpenseEntity>

    @Query("SELECT id FROM expense")
    suspend fun ids(): List<String>

    @Query("SELECT COUNT(*) FROM expense WHERE $LIVE")
    suspend fun activeCount(): Int

    @Query("SELECT currency, $NET AS total FROM expense WHERE $SPEND AND local_date BETWEEN :start AND :end GROUP BY currency ORDER BY total DESC")
    suspend fun totalsSnapshot(start: String, end: String): List<CurrencyTotal>

    @Query("SELECT * FROM expense WHERE $SPEND ORDER BY occurred_at DESC LIMIT :limit")
    suspend fun recentSnapshot(limit: Int): List<ExpenseEntity>
}

@Dao
interface AccountDao {
    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Query("SELECT * FROM account ORDER BY kind, label")
    fun observeAll(): Flow<List<AccountEntity>>
}

@Dao
interface RuleDao {
    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("SELECT * FROM merchant_rule")
    suspend fun all(): List<MerchantRuleEntity>
}
