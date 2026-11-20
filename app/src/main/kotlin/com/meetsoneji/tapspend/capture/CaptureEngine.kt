package com.meetsoneji.tapspend.capture

import com.meetsoneji.tapspend.AppContainer
import com.meetsoneji.tapspend.core.capture.Classifier
import com.meetsoneji.tapspend.core.capture.Direction
import com.meetsoneji.tapspend.core.capture.Hint
import com.meetsoneji.tapspend.core.capture.Instrument
import com.meetsoneji.tapspend.core.capture.LearnedRule
import com.meetsoneji.tapspend.core.capture.ParsedTxn
import com.meetsoneji.tapspend.core.capture.TxnKind
import com.meetsoneji.tapspend.data.AccountEntity
import com.meetsoneji.tapspend.data.ExpenseEntity
import com.meetsoneji.tapspend.data.TxnStatus
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

/**
 * Turns a parsed alert into a transaction waiting for review. Works the same for SMS, payment-app notifications
 * and the 90-day inbox import. Never stores the raw message.
 */
class CaptureEngine(private val c: AppContainer) {
    private val db get() = c.database
    private val dedupeWindowMs = 10 * 60 * 1000L
    private val transferPairWindowMs = 15 * 60 * 1000L

    /** @return the new pending transaction, or null if it was a duplicate or not money spent/received. */
    suspend fun ingest(p: ParsedTxn, source: String, at: Instant, notify: Boolean): ExpenseEntity? {
        if (p.hint == Hint.STATEMENT) return null // statements feed the card check in a later version
        val settings = c.settings.flow.first()
        val rules = db.ruleDao().all().associate { it.merchantKey to LearnedRule(it.kind, it.categoryId) }
        val cls = Classifier.classify(p, settings.userName.split(' ').filter { it.isNotBlank() }, rules)
        val ms = at.toEpochMilli()
        val accountId = ensureAccount(p, ms)
        val dao = db.expenseDao()

        // 1. Same payment seen twice (bank SMS + payment-app alert, or you already logged it by hand)?
        val nearby = dao.sameAmountBetween(p.amountMinor, ms - dedupeWindowMs, ms + dedupeWindowMs)
        val credit = p.direction == Direction.CREDIT
        nearby.firstOrNull { e -> sameDirection(e.kind, credit, cls.kind) && compatible(e, p.ref, accountId) }?.let { dup ->
            if (dup.merchant == null || dup.accountId == null || dup.ref == null) {
                dao.upsert(dup.copy(merchant = dup.merchant ?: p.merchant, accountId = dup.accountId ?: accountId, ref = dup.ref ?: p.ref))
            }
            return null
        }

        var kind = cls.kind
        // 2. Money leaving one of your accounts and arriving in another within minutes is a self-transfer.
        if (kind == TxnKind.EXPENSE || kind == TxnKind.INCOME) {
            val pairWanted = if (kind == TxnKind.EXPENSE) TxnKind.INCOME else TxnKind.EXPENSE
            dao.sameAmountBetween(p.amountMinor, ms - transferPairWindowMs, ms + transferPairWindowMs)
                .firstOrNull { it.kind == pairWanted && it.accountId != null && accountId != null && it.accountId != accountId }
                ?.let { other ->
                    dao.upsert(other.copy(kind = TxnKind.TRANSFER, status = if (other.status == TxnStatus.CONFIRMED && other.kind == TxnKind.INCOME) TxnStatus.CONFIRMED else other.status))
                    kind = TxnKind.TRANSFER
                }
        }

        // 3. Refunds land in the category of the purchase they refund.
        var linked: ExpenseEntity? = null
        if (kind == TxnKind.REFUND && p.merchant != null) linked = dao.recentByMerchant(p.merchant, ms).firstOrNull()

        val categoryId = listOfNotNull(linked?.categoryId, cls.categoryKey, "preset-other")
            .first { db.categoryDao().get(it) != null }
        val zone = c.clock.zone
        val now = c.clock.millis()
        val entity = ExpenseEntity(
            id = UUID.randomUUID().toString(), amountMinor = p.amountMinor, currency = "INR", categoryId = categoryId,
            note = null, occurredAt = ms, zoneId = zone.id, localDate = at.atZone(zone).toLocalDate().toString(),
            createdAt = now, updatedAt = now, kind = kind,
            // Money received is only kept to spot self-transfers; it never needs your review.
            status = if (kind == TxnKind.INCOME) TxnStatus.CONFIRMED else TxnStatus.PENDING,
            source = source, merchant = p.merchant, accountId = accountId, ref = p.ref, linkedId = linked?.id,
        )
        dao.upsert(entity)
        c.widgetUpdater.requestUpdate()
        if (notify && entity.status == TxnStatus.PENDING) ReviewNotifier.notifyNew(c.appContext, entity, cls.reason)
        return entity
    }

    private fun sameDirection(existingKind: String, newIsCredit: Boolean, newKind: String): Boolean {
        if (existingKind == TxnKind.TRANSFER || newKind == TxnKind.TRANSFER) return true
        val existingCredit = existingKind == TxnKind.REFUND || existingKind == TxnKind.CASHBACK || existingKind == TxnKind.INCOME
        return existingCredit == newIsCredit
    }

    private fun compatible(e: ExpenseEntity, ref: String?, accountId: String?): Boolean = when {
        ref != null && e.ref != null -> ref == e.ref
        accountId != null && e.accountId != null -> accountId == e.accountId
        else -> true
    }

    private suspend fun ensureAccount(p: ParsedTxn, ms: Long): String? {
        val last4 = p.last4 ?: return null
        val kind = when (p.instrument) { Instrument.CARD -> "CARD"; Instrument.WALLET -> "WALLET"; else -> "BANK" }
        val id = "${kind.lowercase()}-$last4"
        val existing = db.accountDao().get(id)
        if (existing == null || (existing.institution == null && p.institution != null)) {
            val noun = if (kind == "CARD") "card" else if (kind == "WALLET") "wallet" else "account"
            db.accountDao().upsert(
                AccountEntity(id, kind, p.institution ?: existing?.institution, last4, listOfNotNull(p.institution, noun, "••$last4").joinToString(" "), existing?.statementDay, existing?.createdAt ?: ms),
            )
        }
        return id
    }
}
