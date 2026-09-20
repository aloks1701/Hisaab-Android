package com.aloksharma.hisaab

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    @Test
    fun `parses the notification formats the demo relies on`() {
        val cases = listOf(
            Triple("Google Pay You paid ₹850 to Zomato", 85000L, Category.FOOD),
            Triple("PhonePe Payment of ₹240 to Rapido successful", 24000L, Category.TRANSPORT),
            Triple("Paytm ₹499 paid to Amazon using UPI", 49900L, Category.SHOPPING),
            Triple(
                "SBI Rs 320.00 debited from A/c XX7781 to VPA indianoil@ybl on 20-09-26 Ref 5544",
                32000L, Category.FUEL,
            ),
        )
        for ((text, paise, category) in cases) {
            val parsed = NotificationParser.parse(text)
            assertNotNull("failed to parse: $text", parsed)
            assertEquals(text, TxnType.DEBIT, parsed!!.type)
            assertEquals(text, paise, parsed.amountPaise)
            assertEquals(text, category, parsed.category)
        }
    }

    @Test
    fun `reads a credit and takes the counterparty, not the account number`() {
        val parsed = NotificationParser.parse(
            "HDFC Bank Rs.1,200.00 credited to A/c XX4412 by VPA rahul.s@okhdfc on 20-09-26"
        )!!
        assertEquals(TxnType.CREDIT, parsed.type)
        assertEquals(120000L, parsed.amountPaise)
        assertEquals("rahul.s", parsed.merchant)
        assertEquals(Category.INCOME, parsed.category)
    }

    @Test
    fun `drops notifications that are not payments`() {
        assertNull(NotificationParser.parse("Zomato 50% off on your next order"))
        assertNull(NotificationParser.parse("Flat ₹500 off, no counterparty here"))
        assertNull(NotificationParser.parse("You have 3 new messages"))
    }

    @Test
    fun `flags a guessable merchant for review`() {
        val parsed = NotificationParser.parse("Google Pay You paid ₹150 to Suresh Kumar")!!
        assertEquals(Category.OTHER, parsed.category)
        assertTrue(parsed.needsReview)
    }

    @Test
    fun `money never goes through floating point`() {
        assertEquals(120050L, NotificationParser.toPaise("1,200.50"))
        assertEquals(85000L, NotificationParser.toPaise("850"))
        assertEquals(32000L, NotificationParser.toPaise("320.00"))
        assertEquals(1990L, NotificationParser.toPaise("19.9"))
    }
}

class SummaryTest {

    /** The worked example from the product spec: ₹850 + ₹240 + ₹499 out, ₹1,200 in. */
    private val fixture = listOf(
        Transaction(1, TxnType.DEBIT, 85000, "Zomato", Category.FOOD, "", 1000),
        Transaction(2, TxnType.DEBIT, 24000, "Rapido", Category.TRANSPORT, "", 2000),
        Transaction(3, TxnType.CREDIT, 120000, "rahul.s", Category.INCOME, "", 3000),
        Transaction(4, TxnType.DEBIT, 49900, "Amazon", Category.SHOPPING, "", 4000),
    )

    @Test
    fun `totals match the worked example`() {
        val s = summarize(fixture)
        assertEquals(158900L, s.spentPaise)
        assertEquals(120000L, s.receivedPaise)
        assertEquals(-38900L, s.netPaise)
    }

    @Test
    fun `credits stay out of the category breakdown`() {
        val byCategory = summarize(fixture).byCategory
        assertEquals(3, byCategory.size)
        assertEquals(85000L, byCategory[Category.FOOD])
        assertNull(byCategory[Category.INCOME])
    }

    @Test
    fun `formats rupees with Indian digit grouping`() {
        assertEquals("₹1,589", formatPaise(158900))
        assertEquals("-₹389", formatPaise(-38900))
        assertEquals("₹1,200.50", formatPaise(120050))
        assertEquals("₹0", formatPaise(0))
        assertEquals("₹12,34,567", formatPaise(123456700))
    }
}

class DedupTest {

    /** A bank app and a UPI app both notify the same payment seconds apart. */
    private class FakeDao : TransactionDao {
        val rows = mutableListOf<Transaction>()
        override suspend fun insert(txn: Transaction): Long {
            rows.add(txn.copy(id = rows.size + 1L))
            return rows.size.toLong()
        }
        override fun observeAll(): Flow<List<Transaction>> = flowOf(rows)
        override suspend fun countSimilar(
            amountPaise: Long, merchant: String, since: Long, until: Long,
        ) = rows.count { it.amountPaise == amountPaise && it.merchant == merchant && it.timestamp in since..until }
    }

    @Test
    fun `the same payment reported twice is stored once`() = runBlocking {
        val dao = FakeDao()
        val repo = LedgerRepository(dao)
        val base = 1_700_000_000_000L

        assertNotNull(repo.ingest("Google Pay You paid ₹850 to Zomato", base))
        assertNull(repo.ingest("HDFC Bank Rs.850.00 debited to Zomato", base + 30_000))

        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `the same amount to the same merchant a day later is a real second payment`() = runBlocking {
        val dao = FakeDao()
        val repo = LedgerRepository(dao)
        val base = 1_700_000_000_000L

        assertNotNull(repo.ingest("Google Pay You paid ₹850 to Zomato", base))
        assertNotNull(repo.ingest("Google Pay You paid ₹850 to Zomato", base + 86_400_000))

        assertEquals(2, dao.rows.size)
    }
}
