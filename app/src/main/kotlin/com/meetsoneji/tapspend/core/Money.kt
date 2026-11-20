package com.meetsoneji.tapspend.core

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/**
 * Money is always a Long in minor units (paise, cents) plus an ISO 4217 code. Never Double/Float.
 * Pure JVM code (no Android imports) so it is unit-tested in milliseconds.
 */
object Money {
    /** Largest amount accepted from the keypad, in minor units (99,99,99,999.99 in a 2-decimal currency). */
    const val MAX_MINOR = 9_999_999_999L

    private val EN_IN = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun fractionDigits(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    fun isValidCode(code: String): Boolean =
        code.length == 3 && runCatching { Currency.getInstance(code) }.isSuccess

    /** Converts keypad text like "120" / "120.5" / "0." to minor units. Throws on overflow or bad text. */
    fun parseMinor(text: String, code: String): Long {
        val digits = fractionDigits(code)
        val clean = text.ifEmpty { "0" }.let { if (it.endsWith(".")) it + "0" else it }
        require(clean.matches(Regex("""\d+(\.\d+)?"""))) { "bad amount" }
        val value = BigDecimal(clean)
        require(value.scale() <= digits) { "too many decimals" }
        return value.movePointRight(digits).longValueExact()
    }

    private fun groupingLocale(code: String, locale: Locale): Locale =
        if (code == "INR") EN_IN else locale

    fun symbol(code: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(code).getSymbol(groupingLocale(code, locale)) }.getOrDefault(code)

    /** Groups an unsigned integer string. Indian (3,2,2...) for INR / India locales, otherwise 3-digit groups. */
    fun group(intDigits: String, indian: Boolean, sep: Char): String {
        if (intDigits.length <= 3) return intDigits
        val last3 = intDigits.takeLast(3)
        var rest = intDigits.dropLast(3)
        val parts = ArrayDeque<String>()
        parts.addFirst(last3)
        val size = if (indian) 2 else 3
        while (rest.isNotEmpty()) {
            parts.addFirst(rest.takeLast(size))
            rest = rest.dropLast(size)
        }
        return parts.joinToString(sep.toString())
    }

    /** "₹1,00,000" / "₹120.50" / "$1,234". Fraction shown only when non-zero unless [alwaysFraction]. */
    fun format(
        minor: Long,
        code: String,
        locale: Locale = Locale.getDefault(),
        alwaysFraction: Boolean = false,
    ): String {
        val fd = fractionDigits(code)
        val loc = groupingLocale(code, locale)
        val sym = DecimalFormatSymbols.getInstance(loc)
        val negative = minor < 0
        val abs = BigDecimal.valueOf(kotlin.math.abs(minor)).movePointLeft(fd)
        val plain = abs.setScale(fd).toPlainString()
        val intPart = plain.substringBefore('.')
        val fracPart = if (fd > 0) plain.substringAfter('.') else ""
        val indian = code == "INR" || loc.country == "IN"
        val grouped = group(intPart, indian, sym.groupingSeparator)
        val showFraction = fd > 0 && (alwaysFraction || fracPart.any { it != '0' })
        val body = grouped + if (showFraction) sym.decimalSeparator + fracPart else ""
        return (if (negative) "-" else "") + symbol(code, locale) + body
    }

    /** Live display of what the user has typed so far: keeps a trailing "." and typed zeros ("12.50"). */
    fun formatTyping(text: String, code: String, locale: Locale = Locale.getDefault()): String {
        val loc = groupingLocale(code, locale)
        val sym = DecimalFormatSymbols.getInstance(loc)
        val intPart = text.substringBefore('.').ifEmpty { "0" }
        val indian = code == "INR" || loc.country == "IN"
        val grouped = group(intPart, indian, sym.groupingSeparator)
        val tail = if (text.contains('.')) sym.decimalSeparator + text.substringAfter('.') else ""
        return symbol(code, locale) + grouped + tail
    }

    /** Spoken form for TalkBack: "120 Indian Rupee". */
    fun speech(minor: Long, code: String, locale: Locale = Locale.getDefault()): String {
        val name = runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)
        val fd = fractionDigits(code)
        val number = BigDecimal.valueOf(minor).movePointLeft(fd).stripTrailingZeros().toPlainString()
        return "$number $name"
    }
}
