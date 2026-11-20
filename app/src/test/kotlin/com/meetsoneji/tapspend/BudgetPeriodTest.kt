package com.meetsoneji.tapspend

import com.meetsoneji.tapspend.core.BudgetPeriod
import com.meetsoneji.tapspend.core.CsvExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class BudgetPeriodTest {
    @Test fun calendarMonthWhenStartDayIsOne() {
        val p = BudgetPeriod.containing(LocalDate.of(2026, 11, 20), 1)
        assertEquals(LocalDate.of(2026, 11, 1), p.start)
        assertEquals(LocalDate.of(2026, 11, 30), p.end)
        assertEquals(30, p.days)
    }

    @Test fun salaryCycleBeforeAndAfterStartDay() {
        val before = BudgetPeriod.containing(LocalDate.of(2026, 11, 3), 5)
        assertEquals(LocalDate.of(2026, 10, 5), before.start)
        assertEquals(LocalDate.of(2026, 11, 4), before.end)
        val on = BudgetPeriod.containing(LocalDate.of(2026, 11, 5), 5)
        assertEquals(LocalDate.of(2026, 11, 5), on.start)
        assertEquals(LocalDate.of(2026, 12, 4), on.end)
    }

    @Test fun yearBoundaryAndLeapFebruary() {
        val jan = BudgetPeriod.containing(LocalDate.of(2027, 1, 2), 10)
        assertEquals(LocalDate.of(2026, 12, 10), jan.start)
        assertEquals(LocalDate.of(2027, 1, 9), jan.end)
        val feb = BudgetPeriod.containing(LocalDate.of(2028, 2, 10), 1)
        assertEquals(29, feb.days)
    }

    @Test fun navigationIsContiguous() {
        val p = BudgetPeriod.containing(LocalDate.of(2026, 11, 20), 15)
        val prev = BudgetPeriod.previous(p, 15)
        val next = BudgetPeriod.next(p, 15)
        assertEquals(p.start.minusDays(1), prev.end)
        assertEquals(p.end.plusDays(1), next.start)
        assertTrue(LocalDate.of(2026, 11, 20) in p)
        assertFalse(LocalDate.of(2026, 11, 20) in prev)
    }

    @Test fun startDayIsClampedTo28() {
        val p = BudgetPeriod.containing(LocalDate.of(2026, 2, 28), 31)
        assertEquals(28, p.start.dayOfMonth)
    }

    @Test fun labels() {
        val p = BudgetPeriod.containing(LocalDate.of(2026, 11, 20), 1)
        assertEquals("November 2026", BudgetPeriod.label(p, 1, Locale.ENGLISH))
        val q = BudgetPeriod.containing(LocalDate.of(2026, 11, 20), 5)
        assertEquals("5 Nov – 4 Dec", BudgetPeriod.label(q, 5, Locale.ENGLISH))
    }
}

class CsvExportTest {
    @Test fun formulaInjectionIsNeutralised() {
        assertEquals("'=SUM(A1)", CsvExport.cell("=SUM(A1)"))
        assertEquals("'+1", CsvExport.cell("+1"))
        assertEquals("'-2", CsvExport.cell("-2"))
        assertEquals("'@cmd", CsvExport.cell("@cmd"))
        assertEquals("'\tx", CsvExport.cell("\tx"))
        assertEquals("plain", CsvExport.cell("plain"))
    }

    @Test fun quotingRules() {
        assertEquals("\"a,b\"", CsvExport.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.cell("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExport.cell("line1\nline2"))
        assertEquals("a,b,c", CsvExport.row(listOf("a", "b", "c")))
    }
}
