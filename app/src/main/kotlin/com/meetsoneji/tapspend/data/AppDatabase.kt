package com.meetsoneji.tapspend.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase.Callback
import org.json.JSONArray

@Database(entities = [CategoryEntity::class, ExpenseEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val NAME = "tapspend.db"

        // No fallbackToDestructiveMigration, ever: losing a user's expenses is the worst bug this app can have.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addCallback(SeedCategories(context.applicationContext))
                .build()
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
