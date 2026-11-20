package com.meetsoneji.tapspend.core.capture

import java.math.BigDecimal

enum class Direction { DEBIT, CREDIT }
enum class Instrument { CARD, ACCOUNT, WALLET, UNKNOWN }

/** What the message itself says, before any judgement about whether it's spending. */
enum class Hint { NONE, CARD_PAYMENT_RECEIVED, REFUND, CASHBACK, STATEMENT, ATM }

data class ParsedTxn(
    val direction: Direction,
    val amountMinor: Long,
    val instrument: Instrument,
    val last4: String?,
    val institution: String?,
    val merchant: String?,
    val ref: String?,
    val hint: Hint,
)

/**
 * Parses Indian bank / card / UPI alerts (SMS bodies, or payment-app notification text) entirely on-device.
 * Returns null for anything that is not a completed money movement: OTPs, declines, offers, reminders.
 * Raw text is never stored; only the fields above are kept.
 */
object TxnParser {
    private val OPT = setOf(RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("""(?:INR|Rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", OPT)
    private val AMOUNT_BY = Regex("""(?:debited|credited)\s+by\s+(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", OPT)

    private val IGNORE = listOf(
        Regex("""\bOTP\b|one[\s-]?time\s+password|verification\s+code|\bOTP is\b""", OPT),
        Regex("""declined|unsuccessful|\bfailed\b|could not be processed|insufficient""", OPT),
        Regex("""will be debited|is due on|requested money|collect request|auto[\s-]?pay.*(?:scheduled|registered)|mandate""", OPT),
        Regex("""pre[\s-]?approved|personal loan|loan of|apply now|offer|cashback upto|win\b""", OPT),
    )
    private val STATEMENT = Regex("""statement.*(?:total\s+(?:amt\.?\s*|amount\s*)?due|amount due)""", OPT)
    private val CARD_PAYMENT_RECEIVED = Regex("""payment\s+of\s+.*received.*(?:credit\s+)?card|received\s+(?:a\s+)?payment.*card|payment.*credited\s+to\s+your.*card""", OPT)
    private val REFUND = Regex("""\brefund|revers(?:ed|al)""", OPT)
    private val CASHBACK = Regex("""cash\s?back|reward\s+points?.*(?:redeemed|credited)|points?\s+redeemed""", OPT)
    private val DEBIT = Regex("""\b(?:debited|spent|sent|paid|withdrawn|purchase|used for|charged|thank you for using|txn\s+(?:of\s+)?(?:INR|Rs))""", OPT)
    private val CREDIT = Regex("""\b(?:credited|received|deposited)\b""", OPT)
    private val ATM = Regex("""\bATM\b|cash withdrawal|withdrawn""", OPT)

    private val CARD = Regex("""(?:credit\s+card|debit\s+card|card)(?:\s+ending(?:\s+with)?|\s+no\.?)?\s*[xX*•.]*\s*(\d{4})\b""", OPT)
    private val ACCOUNT = Regex("""\b(?:a/c|ac|acct|account)(?:\s+no\.?)?\s*[xX*•.]*\s*(\d{3,6})\b""", OPT)
    private val DOTS4 = Regex("""[•*xX]{2,}\s?(\d{4})\b""")
    private val UPI_REF = Regex("""(?:UPI\s*(?:Ref\.?|Ref No\.?|:)?|Ref(?:\s*No\.?)?|Refno|RRN)\s*:?\s*(\d{10,14})""", OPT)

    private val MERCHANT = listOf(
        Regex("""\bon\s+\d{1,2}[-/ ][A-Za-z0-9]{2,3}[-/ ]\d{2,4}\s+(?:on|at)\s+([^.]{2,40}?)\.\s""", OPT), // ICICI: "on 16-Nov-26 on AMAZON PAY IN."
        Regex("""\bIST\s+([A-Za-z0-9&'._\- ]{2,40}?)\s+Avl""", OPT), // Axis: "... IST NETFLIX Avl Limit"
        Regex(""";\s*([A-Za-z0-9&'._\- ]{2,40}?)\s+credited""", OPT), // ICICI acct: "debited ...; BLINKIT credited"
        Regex("""trf\s+to\s+([A-Za-z0-9&'._@\- ]{2,40}?)\s+Ref""", OPT), // SBI UPI
        Regex("""\bfrom\s+([A-Za-z0-9&'._\- ]{2,40}?)\s+(?:has been|is)\s""", OPT), // refunds: "from AMAZON has been"
        Regex("""\bat\s+([A-Za-z0-9&'._\-*/ ]{2,40}?)(?:\s+on\b|\s+for\b|\s+via\b|\s+Avl|\s+ref|\.\s|\.$|\s+Not|$)""", OPT),
        Regex("""\bto\s+(?!your\b)([A-Za-z0-9&'._@\- ]{2,40}?)(?:\s+on\b|\s+ref|\s+upi|\.\s|\.$|\s+Not|\s*\(|$)""", OPT),
        Regex("""towards\s+([A-Za-z0-9&'._@\- ]{2,40}?)(?:\s+on\b|\.|$)""", OPT),
    )

    private val INSTITUTIONS = linkedMapOf(
        "HDFC" to Regex("""HDFC""", OPT), "ICICI" to Regex("""ICICI""", OPT), "SBI" to Regex("""\bSBI|SBICRD|SBIUPI|SBIINB|State Bank""", OPT),
        "Axis" to Regex("""\bAXIS""", OPT), "Kotak" to Regex("""KOTAK""", OPT), "Yes Bank" to Regex("""YESBNK|Yes Bank""", OPT),
        "IDFC First" to Regex("""IDFC""", OPT), "IndusInd" to Regex("""INDUS""", OPT), "Amex" to Regex("""AMEX|American Express""", OPT),
        "AU Bank" to Regex("""AUBANK|AU Small""", OPT), "OneCard" to Regex("""ONECRD|OneCard""", OPT), "RBL" to Regex("""\bRBL""", OPT),
        "Federal" to Regex("""FEDBNK|Federal""", OPT), "Paytm" to Regex("""PAYTM""", OPT), "PNB" to Regex("""\bPNB""", OPT),
        "Bank of Baroda" to Regex("""BOB|Baroda""", OPT), "Canara" to Regex("""CANBNK|Canara""", OPT),
    )

    /** DLT sender ids look like "VM-HDFCBK", "AX-ICICIT-S". Personal numbers are never parsed. */
    fun isBankSender(sender: String): Boolean = Regex("""^[A-Z]{2}-[A-Z0-9]{3,9}(-[A-Z])?$""").matches(sender.trim().uppercase())

    fun parse(text: String, source: String = ""): ParsedTxn? {
        val body = text.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (body.length < 12) return null
        val statement = STATEMENT.containsMatchIn(body)
        if (!statement && IGNORE.any { it.containsMatchIn(body) } && !REFUND.containsMatchIn(body)) return null

        val amountText = (AMOUNT_BY.find(body) ?: AMOUNT.find(body))?.groupValues?.get(1) ?: return null
        val amountMinor = runCatching { BigDecimal(amountText.replace(",", "")).movePointRight(2).longValueExact() }.getOrNull() ?: return null
        if (amountMinor <= 0) return null

        val cardPayment = CARD_PAYMENT_RECEIVED.containsMatchIn(body)
        val refund = REFUND.containsMatchIn(body)
        val cashback = CASHBACK.containsMatchIn(body)
        val debit = DEBIT.containsMatchIn(body)
        val credit = CREDIT.containsMatchIn(body)
        val direction = when {
            statement -> Direction.DEBIT
            cardPayment || refund || cashback -> Direction.CREDIT
            debit -> Direction.DEBIT
            credit -> Direction.CREDIT
            else -> return null
        }
        val hint = when {
            statement -> Hint.STATEMENT
            cardPayment -> Hint.CARD_PAYMENT_RECEIVED
            refund -> Hint.REFUND
            cashback -> Hint.CASHBACK
            direction == Direction.DEBIT && ATM.containsMatchIn(body) && !Regex("""\bat\s+(?!ATM)""", OPT).containsMatchIn(body.substringAfter("ATM", "")) -> Hint.ATM
            else -> Hint.NONE
        }
        val card = CARD.find(body)?.groupValues?.get(1)
        val account = ACCOUNT.find(body)?.groupValues?.get(1)
        val dots = DOTS4.find(body)?.groupValues?.get(1)
        val instrument = when {
            card != null -> Instrument.CARD
            account != null -> Instrument.ACCOUNT
            Regex("""wallet""", OPT).containsMatchIn(body) -> Instrument.WALLET
            else -> Instrument.UNKNOWN
        }
        val institution = INSTITUTIONS.entries.firstOrNull { it.value.containsMatchIn(source) }?.key
            // ignore UPI ids like "zomato@icici": the payee's bank is not the payer's bank
            ?: body.replace(Regex("""\S+@\S+"""), " ").let { b -> INSTITUTIONS.entries.firstOrNull { it.value.containsMatchIn(b) }?.key }
        return ParsedTxn(
            direction = direction,
            amountMinor = amountMinor,
            instrument = instrument,
            last4 = card ?: account ?: dots,
            institution = institution,
            merchant = if (statement) null else merchantOf(body),
            ref = UPI_REF.find(body)?.groupValues?.get(1),
            hint = hint,
        )
    }

    fun merchantOf(body: String): String? {
        for (r in MERCHANT) {
            val raw = r.find(body)?.groupValues?.get(1)?.trim() ?: continue
            val cleaned = clean(raw) ?: continue
            return cleaned
        }
        return null
    }

    private val NOT_MERCHANT = Regex("""^(?:your|you|the|a/c|ac|acct|account|card|bank|hdfc bank|icici bank|axis bank|sbi|kotak|rs\.?|inr|\d+)$""", OPT)

    private fun clean(raw: String): String? {
        var m = raw.trim().trimEnd('.', ',', ';', ':', '-').trim()
        m = m.replace(Regex("""\s+(?:on|at|ref|upi)$""", OPT), "")
        if (m.contains('@')) m = m.substringBefore('@') // a UPI id: keep the handle as the name
        m = m.replace(Regex("""^(?:VPA|UPI|POS|ECOM)[\s/:-]+""", OPT), "")
        // Drop legal/locale suffixes banks append: "AMAZON PAY IN", "UBER INDIA PVT LTD".
        m = m.replace(Regex("""(?:\s+(?:IN|INDIA|PVT|PRIVATE|LTD|LIMITED|LLP|INC|CO))+$""", OPT), "").trim()
        if (m.length < 2 || NOT_MERCHANT.matches(m) || m.all { it.isDigit() || it == ' ' }) return null
        // Title case, except short all-consonant abbreviations such as KFC, PVR, BSNL, HDFC.
        return m.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            val abbreviation = w.length <= 5 && w.all { it.isUpperCase() || it.isDigit() } && w.none { it in "AEIOU" }
            if (abbreviation) w else w.lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    /** Stable key for learned rules: "SWIGGY", "Swiggy Ltd" and "swiggy" all map to "swiggy". */
    fun merchantKey(merchant: String): String =
        merchant.lowercase().replace(Regex("""\b(?:pvt|private|ltd|limited|india|in|technologies|services|payments?)\b"""), "")
            .filter { it.isLetterOrDigit() }.ifEmpty { merchant.lowercase().filter { it.isLetterOrDigit() } }
}
