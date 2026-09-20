package com.aloksharma.hisaab

import java.util.Calendar

/**
 * A calendar month, and the window it covers.
 *
 * Deliberately not java.time: minSdk is 24, and pulling in core library desugaring to get
 * YearMonth would be a build-wide change for one small value type.
 */
data class Period(val year: Int, val month: Int) {   // month is 0-based, as Calendar has it

    fun previous(): Period = if (month == 0) Period(year - 1, 11) else Period(year, month - 1)

    fun next(): Period = if (month == 11) Period(year + 1, 0) else Period(year, month + 1)

    /** Inclusive of the first instant of the month, exclusive of the first instant of the next. */
    fun contains(timestampMs: Long): Boolean = timestampMs >= startMs() && timestampMs < next().startMs()

    fun startMs(): Long = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /** Days counted so far: the whole month once it is past, days elapsed while it is current. */
    fun elapsedDays(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val isCurrent = cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        return if (isCurrent) {
            cal.get(Calendar.DAY_OF_MONTH)
        } else {
            Calendar.getInstance().apply {
                clear(); set(Calendar.YEAR, year); set(Calendar.MONTH, month)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }

    companion object {
        fun current(now: Long = System.currentTimeMillis()): Period =
            Calendar.getInstance().apply { timeInMillis = now }
                .let { Period(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }

        /** The twelve months of a year, January first. */
        fun monthsOf(year: Int): List<Period> = (0..11).map { Period(year, it) }
    }
}

fun List<Transaction>.inPeriod(period: Period): List<Transaction> = filter { period.contains(it.timestamp) }

fun List<Transaction>.inYear(year: Int): List<Transaction> = filter {
    Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR) == year
}

/** Debits per month across a year, for the yearly bar strip. Months with no spend give zero. */
fun List<Transaction>.spendByMonth(year: Int): List<Pair<Period, Long>> {
    val rows = inYear(year).filter { it.type == TxnType.DEBIT }
    val byMonth = rows.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.MONTH)
    }
    return Period.monthsOf(year).map { p ->
        p to (byMonth[p.month]?.sumOf { it.amountPaise } ?: 0L)
    }
}
