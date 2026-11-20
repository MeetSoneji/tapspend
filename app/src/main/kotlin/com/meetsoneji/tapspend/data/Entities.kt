package com.meetsoneji.tapspend.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "preset_key") val presetKey: String?,
    /** An emoji. Zero bytes of icon assets, renders offline, and adds a little desi warmth. */
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "color_key") val colorKey: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long? = null,
)

@Immutable
@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("local_date"),
        Index(value = ["category_id", "local_date"]),
        Index("occurred_at"),
        Index("status"),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val note: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    /** EXPENSE, REFUND, CASHBACK, TRANSFER, INCOME or IGNORED (see TxnKind). Only the first three count as spend. */
    @ColumnInfo(defaultValue = "'EXPENSE'") val kind: String = "EXPENSE",
    /** PENDING = auto-detected and waiting for your OK; CONFIRMED = counted. */
    @ColumnInfo(defaultValue = "'CONFIRMED'") val status: String = TxnStatus.CONFIRMED,
    /** MANUAL, SMS or NOTIFICATION. */
    @ColumnInfo(defaultValue = "'MANUAL'") val source: String = TxnSource.MANUAL,
    val merchant: String? = null,
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    /** UPI / bank reference, used to avoid counting the same payment twice. */
    val ref: String? = null,
    /** For a refund: the expense it refunds. */
    @ColumnInfo(name = "linked_id") val linkedId: String? = null,
)

object TxnStatus { const val PENDING = "PENDING"; const val CONFIRMED = "CONFIRMED" }
object TxnSource { const val MANUAL = "MANUAL"; const val SMS = "SMS"; const val NOTIFICATION = "NOTIFICATION" }

/** A card, bank account or wallet seen in your alerts. Id is deterministic ("card-5678") so SMS and app alerts agree. */
@Immutable
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val institution: String?,
    val last4: String?,
    val label: String,
    @ColumnInfo(name = "statement_day") val statementDay: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** What you decided for a merchant, so the same merchant is pre-filled correctly next time. */
@Entity(tableName = "merchant_rule")
data class MerchantRuleEntity(
    @PrimaryKey @ColumnInfo(name = "merchant_key") val merchantKey: String,
    val kind: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class CurrencyTotal(val currency: String, val total: Long)
data class CategoryTotal(val categoryId: String, val currency: String, val total: Long)
data class DayTotal(val localDate: String, val currency: String, val total: Long)
