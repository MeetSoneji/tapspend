package com.meetsoneji.tapspend.core

/** Pure keypad state machine: the typed amount is a String of digits with at most one ".". */
object Keypad {
    fun digit(text: String, d: Char, currency: String): String {
        require(d in '0'..'9')
        val fd = Money.fractionDigits(currency)
        if (text.contains('.')) {
            if (text.substringAfter('.').length >= fd) return text
            return accept(text + d, currency, text)
        }
        if (text == "0") return if (d == '0') text else d.toString().let { accept(it, currency, text) }
        return accept(text + d, currency, text)
    }

    fun dot(text: String, currency: String): String {
        if (Money.fractionDigits(currency) == 0 || text.contains('.')) return text
        return if (text.isEmpty()) "0." else "$text."
    }

    fun backspace(text: String): String = text.dropLast(1)

    fun clear(): String = ""

    /** Rejects input above [Money.MAX_MINOR] before it can reach the database. */
    private fun accept(candidate: String, currency: String, previous: String): String =
        runCatching { Money.parseMinor(candidate, currency) }
            .map { if (it > Money.MAX_MINOR) previous else candidate }
            .getOrDefault(previous)

    fun toMinor(text: String, currency: String): Long =
        runCatching { Money.parseMinor(text, currency) }.getOrDefault(0L)

    /** When the currency changes mid-entry, trim decimals that the new currency does not allow. */
    fun retarget(text: String, currency: String): String {
        val fd = Money.fractionDigits(currency)
        if (!text.contains('.')) return text
        if (fd == 0) return text.substringBefore('.')
        return text.substringBefore('.') + "." + text.substringAfter('.').take(fd)
    }
}
