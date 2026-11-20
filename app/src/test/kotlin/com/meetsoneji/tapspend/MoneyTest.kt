package com.meetsoneji.tapspend

import com.meetsoneji.tapspend.core.Keypad
import com.meetsoneji.tapspend.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {
    private val us = Locale.US

    @Test fun parsesWholeAndFractionalAmounts() {
        assertEquals(12_000L, Money.parseMinor("120", "INR"))
        assertEquals(12_050L, Money.parseMinor("120.5", "INR"))
        assertEquals(12_005L, Money.parseMinor("120.05", "USD"))
        assertEquals(120L, Money.parseMinor("120", "JPY")) // zero-decimal currency
        assertEquals(0L, Money.parseMinor("", "INR"))
        assertEquals(1_200L, Money.parseMinor("12.", "INR")) // trailing dot while typing
    }

    @Test fun rejectsTooManyDecimalsAndGarbage() {
        assertThrows(IllegalArgumentException::class.java) { Money.parseMinor("1.234", "INR") }
        assertThrows(IllegalArgumentException::class.java) { Money.parseMinor("1.5", "JPY") }
        assertThrows(IllegalArgumentException::class.java) { Money.parseMinor("abc", "INR") }
    }

    @Test fun indianGroupingForRupees() {
        assertEquals("₹1,00,000", Money.format(10_000_000, "INR", us))
        assertEquals("₹4,200", Money.format(420_000, "INR", us))
        assertEquals("₹12,34,567.50", Money.format(123_456_750, "INR", us))
        assertEquals("₹999", Money.format(99_900, "INR", us))
        assertEquals("₹0", Money.format(0, "INR", us))
    }

    @Test fun westernGroupingForOtherCurrencies() {
        assertEquals("$1,234.50", Money.format(123_450, "USD", us))
        assertEquals("$1,000,000", Money.format(100_000_000, "USD", us))
    }

    @Test fun alwaysFractionShowsZeros() {
        assertEquals("₹120.00", Money.format(12_000, "INR", us, alwaysFraction = true))
    }

    @Test fun typingDisplayKeepsDotAndTrailingZeros() {
        assertEquals("₹0", Money.formatTyping("", "INR", us))
        assertEquals("₹12.", Money.formatTyping("12.", "INR", us))
        assertEquals("₹1,200.50", Money.formatTyping("1200.50", "INR", us))
        assertEquals("₹1,00,000", Money.formatTyping("100000", "INR", us))
    }

    @Test fun speechReadsAsMoney() {
        assertEquals("120.5 Indian Rupee", Money.speech(12_050, "INR", us))
    }

    @Test fun keypadBuildsAmountsAndEnforcesRules() {
        var t = ""
        t = Keypad.digit(t, '1', "INR"); t = Keypad.digit(t, '2', "INR"); t = Keypad.digit(t, '0', "INR")
        assertEquals("120", t)
        assertEquals(12_000L, Keypad.toMinor(t, "INR"))
        // leading zeros collapse
        assertEquals("5", Keypad.digit(Keypad.digit("", '0', "INR"), '5', "INR"))
        assertEquals("0", Keypad.digit("0", '0', "INR"))
        // max 2 decimals
        var d = Keypad.dot("12", "INR")
        d = Keypad.digit(d, '3', "INR"); d = Keypad.digit(d, '4', "INR"); d = Keypad.digit(d, '5', "INR")
        assertEquals("12.34", d)
        // only one dot, and dot is ignored for zero-decimal currencies
        assertEquals("12.", Keypad.dot("12.", "INR"))
        assertEquals("12", Keypad.dot("12", "JPY"))
        assertEquals("0.", Keypad.dot("", "INR"))
        // backspace and clear
        assertEquals("12", Keypad.backspace("120"))
        assertEquals("", Keypad.clear())
    }

    @Test fun keypadRejectsOverflowBeforeTheDatabase() {
        // Max is 9,999,999,999 minor units = 99,999,999.99 in a 2-decimal currency.
        var t = ""
        repeat(8) { t = Keypad.digit(t, '9', "INR") }
        assertEquals("99999999", t)
        t = Keypad.dot(t, "INR"); t = Keypad.digit(t, '9', "INR"); t = Keypad.digit(t, '9', "INR")
        assertEquals("99999999.99", t)
        assertEquals(Money.MAX_MINOR, Keypad.toMinor(t, "INR"))
        // one more digit in the integer part would overflow; it is refused
        assertEquals("99999999", Keypad.digit("99999999", '9', "INR"))
    }

    @Test fun currencySwitchTrimsDecimals() {
        assertEquals("12", Keypad.retarget("12.50", "JPY"))
        assertEquals("12.5", Keypad.retarget("12.5", "INR"))
        assertTrue(Money.isValidCode("INR"))
        assertTrue(!Money.isValidCode("XXQ"))
    }
}
