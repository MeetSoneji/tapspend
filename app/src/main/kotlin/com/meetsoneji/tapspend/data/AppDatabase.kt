package com.meetsoneji.tapspend.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase.Callback
import org.json.JSONArray

@Database(entities = [CategoryEntity::class, ExpenseEntity::class, AccountEntity::class, MerchantRuleEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun accountDao(): AccountDao
    abstract fun ruleDao(): RuleDao

    companion object {
        const val NAME = "tapspend.db"

        // No fallbackToDestructiveMigration, ever: losing a user's expenses is the worst bug this app can have.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addCallback(SeedCategories(context.applicationContext))
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * v1 -> v2: adds auto-capture columns and tables. Purely additive: every existing expense keeps its data and
 * becomes a confirmed, manual EXPENSE via the column defaults.
 */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'EXPENSE'")
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'CONFIRMED'")
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `merchant` TEXT")
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `account_id` TEXT")
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `ref` TEXT")
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `linked_id` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_status` ON `expense` (`status`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `account` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `institution` TEXT, `last4` TEXT, `label` TEXT NOT NULL, `statement_day` INTEGER, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_rule` (`merchant_key` TEXT NOT NULL, `kind` TEXT NOT NULL, `category_id` TEXT, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`merchant_key`))")
    }
}

/** Seeds the default categories from assets/default_categories.json on first database creation. */
private class SeedCategories(private val context: Context) : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val json = context.assets.open("default_categories.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val key = o.getString("key")
            val cv = ContentValues().apply {
                put("id", "preset-$key")
                put("name", o.getString("name"))
                put("preset_key", key)
                put("icon_key", o.getString("icon"))
                put("color_key", o.getString("color"))
                put("sort_order", i)
                put("is_archived", 0)
            }
            db.insert("category", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv)
        }
    }
}
