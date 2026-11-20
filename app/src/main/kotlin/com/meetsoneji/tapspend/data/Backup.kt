package com.meetsoneji.tapspend.data

import com.meetsoneji.tapspend.core.Money
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { TOO_LARGE, CORRUPT, NOT_A_BACKUP, WRONG_VERSION, WRONG_PASSWORD, PASSWORD_REQUIRED }
}

data class BackupSettings(
    val userName: String,
    val currency: String,
    val monthlyBudgetMinor: Long?,
    val monthStartDay: Int,
)

data class BackupData(
    val categories: List<CategoryEntity>,
    val expenses: List<ExpenseEntity>,
    val settings: BackupSettings?,
)

/**
 * Versioned JSON backup. Strict validation, 10 MB cap, no Java serialization, no zip extraction.
 * Pure JVM (org.json), so the whole round trip is unit-tested.
 */
object BackupCodec {
    const val FORMAT = "tapspend-backup"
    const val VERSION = 1
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_EXPENSES = 200_000
    private const val MAX_CATEGORIES = 500
    private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun encode(data: BackupData, appVersion: String, exportedAtMillis: Long): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("appVersion", appVersion)
        root.put("exportedAt", exportedAtMillis)
        data.settings?.let {
            root.put(
                "settings",
                JSONObject().put("userName", it.userName).put("currency", it.currency)
                    .put("monthlyBudgetMinor", it.monthlyBudgetMinor ?: JSONObject.NULL)
                    .put("monthStartDay", it.monthStartDay),
            )
        }
        val cats = JSONArray()
        data.categories.forEach { c ->
            cats.put(
                JSONObject().put("id", c.id).put("name", c.name).put("presetKey", c.presetKey ?: JSONObject.NULL)
                    .put("iconKey", c.iconKey).put("colorKey", c.colorKey).put("sortOrder", c.sortOrder)
                    .put("isArchived", c.isArchived).put("lastUsedAt", c.lastUsedAt ?: JSONObject.NULL),
            )
        }
        root.put("categories", cats)
        val exps = JSONArray()
        data.expenses.forEach { e ->
            exps.put(
                JSONObject().put("id", e.id).put("amountMinor", e.amountMinor).put("currency", e.currency)
                    .put("categoryId", e.categoryId).put("note", e.note ?: JSONObject.NULL)
                    .put("occurredAt", e.occurredAt).put("zoneId", e.zoneId).put("localDate", e.localDate)
                    .put("createdAt", e.createdAt).put("updatedAt", e.updatedAt),
            )
        }
        root.put("expenses", exps)
        return root.toString()
    }

    fun decode(bytes: ByteArray): BackupData {
        if (bytes.size > MAX_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE, "File too large")
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            if (root.optString("format") != FORMAT) {
                throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Not a Tapspend backup")
            }
            if (root.getInt("version") != VERSION) {
                throw BackupException(BackupException.Reason.WRONG_VERSION, "Unsupported backup version")
            }
            val cats = root.getJSONArray("categories")
            val exps = root.getJSONArray("expenses")
            if (cats.length() > MAX_CATEGORIES || exps.length() > MAX_EXPENSES) corrupt("Too many records")

            val categories = (0 until cats.length()).map { parseCategory(cats.getJSONObject(it)) }
            val ids = categories.map { it.id }.toSet()
            if (ids.size != categories.size) corrupt("Duplicate category ids")
            val expenses = (0 until exps.length()).map { parseExpense(exps.getJSONObject(it), ids) }
            if (expenses.map { it.id }.toSet().size != expenses.size) corrupt("Duplicate expense ids")

            val settings = root.optJSONObject("settings")?.let {
                val code = it.getString("currency")
                if (!Money.isValidCode(code)) corrupt("Bad currency")
                BackupSettings(
                    userName = it.optString("userName", "").take(30),
                    currency = code,
                    monthlyBudgetMinor = if (it.isNull("monthlyBudgetMinor")) null else it.getLong("monthlyBudgetMinor").also { b ->
                        if (b !in 1..Money.MAX_MINOR * 100) corrupt("Bad budget")
                    },
                    monthStartDay = it.getInt("monthStartDay").also { d -> if (d !in 1..28) corrupt("Bad start day") },
                )
            }
            return BackupData(categories, expenses, settings)
        } catch (e: JSONException) {
            throw BackupException(BackupException.Reason.CORRUPT, "File is damaged")
        }
    }

    private fun corrupt(msg: String): Nothing = throw BackupException(BackupException.Reason.CORRUPT, msg)

    private fun parseCategory(o: JSONObject): CategoryEntity {
        val id = o.getString("id")
        val name = o.getString("name")
        val icon = o.getString("iconKey")
        val color = o.getString("colorKey")
        if (id.isBlank() || id.length > 64 || name.isBlank() || name.length > 40 || icon.isEmpty() || icon.length > 16 || color.length > 20) {
            corrupt("Bad category")
        }
        return CategoryEntity(
            id = id, name = name, presetKey = if (o.isNull("presetKey")) null else o.getString("presetKey").take(32),
            iconKey = icon, colorKey = color, sortOrder = o.getInt("sortOrder"),
            isArchived = o.getBoolean("isArchived"), lastUsedAt = if (o.isNull("lastUsedAt")) null else o.getLong("lastUsedAt"),
        )
    }

    private fun parseExpense(o: JSONObject, categoryIds: Set<String>): ExpenseEntity {
        val id = o.getString("id")
        val amount = o.getLong("amountMinor")
        val currency = o.getString("currency")
        val categoryId = o.getString("categoryId")
        val note = if (o.isNull("note")) null else o.getString("note")
        val zone = o.getString("zoneId")
        val localDate = o.getString("localDate")
        val occurredAt = o.getLong("occurredAt")
        if (id.isBlank() || id.length > 64) corrupt("Bad expense id")
        if (amount !in 1..Money.MAX_MINOR) corrupt("Bad amount")
        if (!Money.isValidCode(currency)) corrupt("Bad currency")
        if (categoryId !in categoryIds) corrupt("Expense points to a missing category")
        if (note != null && note.length > 200) corrupt("Note too long")
        if (occurredAt <= 0) corrupt("Bad date")
        if (!DATE_RE.matches(localDate)) corrupt("Bad local date")
        try {
            ZoneId.of(zone)
            LocalDate.parse(localDate)
        } catch (e: Exception) {
            corrupt("Bad date or zone")
        }
        return ExpenseEntity(
            id = id, amountMinor = amount, currency = currency, categoryId = categoryId, note = note,
            occurredAt = occurredAt, zoneId = zone, localDate = localDate,
            createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deletedAt = null,
        )
    }
}

/**
 * Optional password protection. AES-256-GCM, key from PBKDF2-HMAC-SHA256 (310,000 iterations),
 * 16-byte random salt, 12-byte random IV, 4-byte magic header that doubles as the version. javax.crypto only.
 */
object BackupCrypto {
    private val MAGIC = "TSB1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 310_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size + SALT_LEN + IV_LEN && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(blob)) throw BackupException(BackupException.Reason.CORRUPT, "Not an encrypted backup")
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val iv = blob.copyOfRange(MAGIC.size + SALT_LEN, MAGIC.size + SALT_LEN + IV_LEN)
        val body = blob.copyOfRange(MAGIC.size + SALT_LEN + IV_LEN, blob.size)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            return cipher.doFinal(body)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD, "Wrong password or damaged file")
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD, "Wrong password or damaged file")
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Single entry point for import: decrypt if needed, then parse. */
    fun open(bytes: ByteArray, password: CharArray?): BackupData {
        if (bytes.size > BackupCodec.MAX_BYTES + 64) throw BackupException(BackupException.Reason.TOO_LARGE, "File too large")
        val plain = if (isEncrypted(bytes)) {
            if (password == null || password.isEmpty()) {
                throw BackupException(BackupException.Reason.PASSWORD_REQUIRED, "Password needed")
            }
            decrypt(bytes, password)
        } else {
            bytes
        }
        return BackupCodec.decode(plain)
    }
}
