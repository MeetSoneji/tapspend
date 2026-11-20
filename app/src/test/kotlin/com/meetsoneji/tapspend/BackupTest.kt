package com.meetsoneji.tapspend

import com.meetsoneji.tapspend.data.BackupCodec
import com.meetsoneji.tapspend.data.BackupCrypto
import com.meetsoneji.tapspend.data.BackupData
import com.meetsoneji.tapspend.data.BackupException
import com.meetsoneji.tapspend.data.BackupSettings
import com.meetsoneji.tapspend.data.CategoryEntity
import com.meetsoneji.tapspend.data.ExpenseEntity
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    private val cat = CategoryEntity("preset-food", "Khana-Peena", "food", "🍛", "amber", 0, false, 1_700_000_000_000)
    private val cat2 = CategoryEntity("c2", "Chai", null, "☕", "orange", 1, true, null)
    private fun expense(i: Int, note: String? = "chai $i") = ExpenseEntity(
        id = "e$i", amountMinor = 1_000L + i, currency = "INR", categoryId = "preset-food", note = note,
        occurredAt = 1_700_000_000_000 + i, zoneId = "Asia/Kolkata", localDate = "2026-11-20", createdAt = 1, updatedAt = 2, deletedAt = null,
    )
    private val data = BackupData(listOf(cat, cat2), listOf(expense(1), expense(2, null)), BackupSettings("Meet", "INR", 5_000_000L, 5))

    private fun assertReason(reason: BackupException.Reason, block: () -> Unit) {
        try { block(); fail("expected $reason") } catch (e: BackupException) { assertEquals(reason, e.reason) }
    }

    @Test fun jsonRoundTripIsIdentical() {
        val json = BackupCodec.encode(data, "1.0.0", 123L)
        val back = BackupCodec.decode(json.toByteArray())
        assertEquals(data.categories, back.categories)
        assertEquals(data.expenses, back.expenses)
        assertEquals(data.settings, back.settings)
    }

    @Test fun encryptedRoundTripAndWrongPassword() {
        val plain = BackupCodec.encode(data, "1.0.0", 1L).toByteArray()
        val blob = BackupCrypto.encrypt(plain, "sahi-password".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(blob))
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("Khana-Peena")) // not readable as plaintext
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, "sahi-password".toCharArray()))
        assertEquals(data.expenses, BackupCrypto.open(blob, "sahi-password".toCharArray()).expenses)
        assertReason(BackupException.Reason.WRONG_PASSWORD) { BackupCrypto.open(blob, "galat".toCharArray()) }
        assertReason(BackupException.Reason.PASSWORD_REQUIRED) { BackupCrypto.open(blob, null) }
    }

    @Test fun tamperedCiphertextIsRejected() {
        val blob = BackupCrypto.encrypt("hello".toByteArray(), "pw".toCharArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertReason(BackupException.Reason.WRONG_PASSWORD) { BackupCrypto.decrypt(blob, "pw".toCharArray()) }
    }

    @Test fun twoEncryptionsOfTheSameDataDiffer() {
        val a = BackupCrypto.encrypt("x".toByteArray(), "pw".toCharArray())
        val b = BackupCrypto.encrypt("x".toByteArray(), "pw".toCharArray())
        assertFalse(a.contentEquals(b)) // random salt + IV
    }

    @Test fun rejectsCorruptWrongVersionAndNotABackup() {
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode("{ not json".toByteArray()) }
        assertReason(BackupException.Reason.NOT_A_BACKUP) { BackupCodec.decode("""{"format":"other","version":1}""".toByteArray()) }
        val future = JSONObject(BackupCodec.encode(data, "1", 1)).put("version", 99).toString()
        assertReason(BackupException.Reason.WRONG_VERSION) { BackupCodec.decode(future.toByteArray()) }
    }

    @Test fun rejectsOversizedFiles() {
        assertReason(BackupException.Reason.TOO_LARGE) { BackupCodec.decode(ByteArray(BackupCodec.MAX_BYTES + 1)) }
    }

    @Test fun rejectsInvalidRecords() {
        fun mutate(f: (JSONObject) -> Unit): ByteArray {
            val root = JSONObject(BackupCodec.encode(data, "1", 1))
            f(root.getJSONArray("expenses").getJSONObject(0))
            return root.toString().toByteArray()
        }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("amountMinor", 0) }) }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("amountMinor", -5) }) }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("amountMinor", Long.MAX_VALUE) }) }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("currency", "ZZZZ") }) }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("categoryId", "ghost") }) }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("localDate", "not-a-date") }) }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("zoneId", "Mars/Olympus") }) }
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(mutate { it.put("note", "x".repeat(201)) }) }
    }

    @Test fun rejectsDuplicateIds() {
        val dup = data.copy(expenses = listOf(expense(1), expense(1)))
        assertReason(BackupException.Reason.CORRUPT) { BackupCodec.decode(BackupCodec.encode(dup, "1", 1).toByteArray()) }
    }

    @Test fun softDeletedExpensesAreNeverRestoredAsDeleted() {
        val deleted = expense(3).copy(deletedAt = 5L)
        val back = BackupCodec.decode(BackupCodec.encode(data.copy(expenses = listOf(deleted)), "1", 1).toByteArray())
        assertEquals(null, back.expenses.single().deletedAt)
    }
}
