package com.meetsoneji.tapspend.core.capture

/** Final judgement about a parsed message. Kinds match the `expense.kind` column. */
object TxnKind {
    const val EXPENSE = "EXPENSE"
    const val REFUND = "REFUND"
    const val CASHBACK = "CASHBACK"
    const val TRANSFER = "TRANSFER"
    const val INCOME = "INCOME"
    const val IGNORED = "IGNORED"
}

data class Classification(val kind: String, val categoryKey: String?, val merchantKey: String?, val reason: String)

data class LearnedRule(val kind: String, val categoryId: String?)

object Classifier {
    private val CARD_BILL = Regex("""\bcred\b|cred club|credit\s*card|\bcc\s*(?:bill|payment)|card\s*(?:bill|payment|dues)|billdesk.*card|towards.*card""", RegexOption.IGNORE_CASE)
    private val WALLET_LOAD = Regex("""add(?:ed)?\s*money|wallet\s*(?:load|top.?up)|paytm\s*wallet""", RegexOption.IGNORE_CASE)
    private val INVESTMENT = Regex("""zerodha|groww|\bupstox|kuvera|coin by|mutual\s*fund|\bsip\b|\bnps\b|\bppf\b|indian clearing|bse ltd|nse clearing""", RegexOption.IGNORE_CASE)
    private val SELF = Regex("""\bself\b|own account|to yourself""", RegexOption.IGNORE_CASE)

    /** Ordered: more specific keys first ("instamart" before "swiggy"). Values are preset category keys. */
    val MERCHANT_CATEGORIES: List<Pair<String, String>> = listOf(
        "instamart" to "groceries", "blinkit" to "groceries", "zepto" to "groceries", "bigbasket" to "groceries", "dmart" to "groceries",
        "jiomart" to "groceries", "reliance fresh" to "groceries", "reliance smart" to "groceries", "more retail" to "groceries",
        "licious" to "groceries", "country delight" to "groceries", "milkbasket" to "groceries", "nature" to "groceries",
        "chaayos" to "chai", "chai" to "chai", "starbucks" to "chai", "cafe coffee day" to "chai", "ccd" to "chai", "third wave" to "chai", "blue tokai" to "chai", "bakery" to "chai", "tea" to "chai",
        "swiggy" to "food", "zomato" to "food", "eatsure" to "food", "domino" to "food", "mcdonald" to "food", "kfc" to "food", "pizza" to "food",
        "burger" to "food", "subway" to "food", "haldiram" to "food", "biryani" to "food", "restaurant" to "food", "dhaba" to "food", "kitchen" to "food", "box8" to "food", "faasos" to "food",
        "uber" to "transport", "ola" to "transport", "rapido" to "transport", "namma yatri" to "transport", "blusmart" to "transport", "irctc" to "transport",
        "metro" to "transport", "fastag" to "transport", "petrol" to "transport", "fuel" to "transport", "hpcl" to "transport", "bpcl" to "transport",
        "indian oil" to "transport", "iocl" to "transport", "shell" to "transport", "redbus" to "transport", "indigo" to "transport", "akasa" to "transport", "air india" to "transport", "yulu" to "transport",
        "amazon" to "shopping", "flipkart" to "shopping", "myntra" to "shopping", "ajio" to "shopping", "nykaa" to "shopping", "meesho" to "shopping",
        "tata cliq" to "shopping", "croma" to "shopping", "reliance digital" to "shopping", "decathlon" to "shopping", "ikea" to "shopping", "lenskart" to "shopping", "uniqlo" to "shopping", "zara" to "shopping", "h&m" to "shopping",
        "airtel" to "bills", "jio" to "bills", "vodafone" to "bills", "vi " to "bills", "bsnl" to "bills", "electricity" to "bills", "bescom" to "bills",
        "msedcl" to "bills", "tata power" to "bills", "adani" to "bills", "indane" to "bills", "bharat gas" to "bills", "hp gas" to "bills", "broadband" to "bills",
        "act fibernet" to "bills", "hathway" to "bills", "water" to "bills", "insurance" to "bills", "lic" to "bills", "rent" to "bills", "maintenance" to "bills",
        "apollo" to "health", "pharmeasy" to "health", "1mg" to "health", "netmeds" to "health", "medplus" to "health", "hospital" to "health",
        "clinic" to "health", "practo" to "health", "cult" to "health", "diagnostic" to "health", "pathlab" to "health", "pharma" to "health",
        "netflix" to "entertainment", "hotstar" to "entertainment", "prime video" to "entertainment", "spotify" to "entertainment",
        "bookmyshow" to "entertainment", "pvr" to "entertainment", "inox" to "entertainment", "youtube" to "entertainment", "sonyliv" to "entertainment",
        "zee5" to "entertainment", "steam" to "entertainment", "playstation" to "entertainment", "district" to "entertainment",
    )

    fun guessCategory(merchant: String?): String? {
        val m = " " + (merchant ?: return null).lowercase() + " "
        return MERCHANT_CATEGORIES.firstOrNull { (k, _) -> m.contains(k) || m.replace(" ", "").contains(k.replace(" ", "")) }?.second
    }

    /**
     * @param ownNames the user's name tokens, used to spot transfers to themselves ("Meet Soneji").
     * @param rules what the user decided earlier for a merchant; always wins over the built-in guess.
     */
    fun classify(p: ParsedTxn, ownNames: List<String>, rules: Map<String, LearnedRule>): Classification {
        val key = p.merchant?.let { TxnParser.merchantKey(it) }
        val learned = key?.let { rules[it] }
        val categoryGuess = learned?.categoryId ?: guessCategory(p.merchant)?.let { "preset-$it" }
        return when {
            p.hint == Hint.STATEMENT -> Classification(TxnKind.IGNORED, null, key, "card statement")
            p.hint == Hint.CARD_PAYMENT_RECEIVED -> Classification(TxnKind.TRANSFER, null, key, "card bill paid")
            p.hint == Hint.REFUND -> Classification(TxnKind.REFUND, categoryGuess, key, "refund")
            p.hint == Hint.CASHBACK -> Classification(TxnKind.CASHBACK, null, key, "cashback or rewards")
            p.direction == Direction.CREDIT -> Classification(TxnKind.INCOME, null, key, "money received")
            learned != null -> Classification(learned.kind, learned.categoryId, key, "your earlier choice")
            p.hint == Hint.ATM -> Classification(TxnKind.TRANSFER, null, key, "cash withdrawal")
            p.merchant != null && CARD_BILL.containsMatchIn(p.merchant) -> Classification(TxnKind.TRANSFER, null, key, "card bill payment")
            p.merchant != null && WALLET_LOAD.containsMatchIn(p.merchant) -> Classification(TxnKind.TRANSFER, null, key, "wallet top-up")
            p.merchant != null && INVESTMENT.containsMatchIn(p.merchant) -> Classification(TxnKind.TRANSFER, null, key, "investment")
            p.merchant != null && (SELF.containsMatchIn(p.merchant) || isOwnName(p.merchant, ownNames)) -> Classification(TxnKind.TRANSFER, null, key, "to yourself")
            else -> Classification(TxnKind.EXPENSE, categoryGuess, key, if (categoryGuess != null) "known merchant" else "new merchant")
        }
    }

    /** "Meet Soneji" matches payee "MEET SONEJI" or "Meet S Soneji"; a single first name alone is not enough. */
    fun isOwnName(payee: String, ownNames: List<String>): Boolean {
        val tokens = ownNames.map { it.lowercase().trim() }.filter { it.length >= 2 }
        if (tokens.size < 2) return false
        val p = payee.lowercase()
        return tokens.all { p.contains(it) }
    }
}
