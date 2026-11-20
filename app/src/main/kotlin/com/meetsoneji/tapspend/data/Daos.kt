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

@Dao
interface ExpenseDao {
    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Upsert
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT * FROM expense WHERE id = :id AND deleted_at IS NULL")
    suspend fun get(id: String): ExpenseEntity?

    @Query("SELECT * FROM expense WHERE id = :id AND deleted_at IS NULL")
    fun observe(id: String): Flow<ExpenseEntity?>

    @Query("UPDATE expense SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE expense SET deleted_at = NULL WHERE id = :id")
    suspend fun undoDelete(id: String)

    @Query("DELETE FROM expense WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query(
        "SELECT currency, SUM(amount_minor) AS total FROM expense " +
            "WHERE deleted_at IS NULL AND local_date BETWEEN :start AND :end GROUP BY currency ORDER BY total DESC",
    )
    fun observeTotals(start: String, end: String): Flow<List<CurrencyTotal>>

    @Query(
        "SELECT category_id AS categoryId, currency, SUM(amount_minor) AS total FROM expense " +
            "WHERE deleted_at IS NULL AND local_date BETWEEN :start AND :end GROUP BY category_id, currency",
    )
    fun observeCategoryTotals(start: String, end: String): Flow<List<CategoryTotal>>

    @Query(
        "SELECT local_date AS localDate, currency, SUM(amount_minor) AS total FROM expense " +
            "WHERE deleted_at IS NULL AND local_date BETWEEN :start AND :end GROUP BY local_date, currency",
    )
    fun observeDailyTotals(start: String, end: String): Flow<List<DayTotal>>

    @Query(
        "SELECT currency, SUM(amount_minor) AS total FROM expense " +
            "WHERE deleted_at IS NULL AND local_date = :date GROUP BY currency",
    )
    fun observeDayTotal(date: String): Flow<List<CurrencyTotal>>

    @Query("SELECT * FROM expense WHERE deleted_at IS NULL ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expense WHERE deleted_at IS NULL AND local_date BETWEEN :start AND :end " +
            "AND (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND (:query IS NULL OR note LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY occurred_at DESC",
    )
    fun observeHistory(start: String, end: String, categoryId: String?, query: String?): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE deleted_at IS NULL ORDER BY occurred_at DESC")
    suspend fun allActive(): List<ExpenseEntity>

    @Query("SELECT id FROM expense")
    suspend fun ids(): List<String>

    @Query("SELECT COUNT(*) FROM expense WHERE deleted_at IS NULL")
    suspend fun activeCount(): Int

    // Snapshot reads for the widget (single read, no long-lived collection).
    @Query(
        "SELECT currency, SUM(amount_minor) AS total FROM expense " +
            "WHERE deleted_at IS NULL AND local_date BETWEEN :start AND :end GROUP BY currency ORDER BY total DESC",
    )
    suspend fun totalsSnapshot(start: String, end: String): List<CurrencyTotal>

    @Query("SELECT * FROM expense WHERE deleted_at IS NULL ORDER BY occurred_at DESC LIMIT :limit")
    suspend fun recentSnapshot(limit: Int): List<ExpenseEntity>
}
