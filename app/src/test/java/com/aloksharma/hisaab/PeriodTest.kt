package com.aloksharma.hisaab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PeriodTest {

    private fun at(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear(); set(year, month, day, 12, 0)
    }.timeInMillis

    private fun txn(ts: Long, rupees: Long = 100, type: TxnType = TxnType.DEBIT) =
        Transaction(0, type, rupees * 100, "M", Category.OTHER, "", ts)

    @Test
    fun `steps across a year boundary in both directions`() {
        assertEquals(Period(2025, 11), Period(2026, 0).previous())
        assertEquals(Period(2027, 0), Period(2026, 11).next())
    }

    @Test
    fun `contains its own month and excludes the neighbours`() {
        val sept = Period(2026, 8)
        assertTrue(sept.contains(at(2026, 8, 1)))
        assertTrue(sept.contains(at(2026, 8, 30)))
        assertFalse(sept.contains(at(2026, 7, 31)))
        assertFalse(sept.contains(at(2026, 9, 1)))
    }

    @Test
    fun `a past month counts all its days, so its average is not inflated`() {
        // February 2026 is not a leap year; a past month should count the whole month.
        assertEquals(28, Period(2026, 1).elapsedDays(now = at(2026, 8, 20)))
        assertEquals(30, Period(2026, 3).elapsedDays(now = at(2026, 8, 20)))
    }

    @Test
    fun `the current month counts only days elapsed`() {
        assertEquals(20, Period(2026, 8).elapsedDays(now = at(2026, 8, 20)))
    }

    @Test
    fun `filters transactions to a month`() {
        val rows = listOf(txn(at(2026, 7, 15)), txn(at(2026, 8, 2)), txn(at(2026, 8, 25)))
        assertEquals(2, rows.inPeriod(Period(2026, 8)).size)
    }

    @Test
    fun `spend by month covers all twelve months and counts debits only`() {
        val rows = listOf(
            txn(at(2026, 0, 5), 100),
            txn(at(2026, 0, 9), 50),
            txn(at(2026, 5, 1), 700),
            txn(at(2026, 5, 2), 900, TxnType.CREDIT),
        )
        val byMonth = rows.spendByMonth(2026)
        assertEquals(12, byMonth.size)
        assertEquals(15000L, byMonth[0].second)
        assertEquals(0L, byMonth[1].second)
        assertEquals(70000L, byMonth[5].second)   // the credit is excluded
    }

    @Test
    fun `ignores transactions from another year`() {
        val rows = listOf(txn(at(2025, 5, 1)), txn(at(2026, 5, 1)))
        assertEquals(1, rows.inYear(2026).size)
    }
}
