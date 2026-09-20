package com.aloksharma.hisaab

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room + real SQLite, unlike LedgerTest.kt's fake DAO. Catches what a fake can't:
 * @Entity mapping, the Category/TxnType TypeConverter round trip, Flow query emission, and
 * the countSimilar dedup SQL window.
 */
@RunWith(AndroidJUnit4::class)
class LedgerDatabaseTest {

    private lateinit var db: HisaabDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HisaabDatabase::class.java).build()
        dao = db.transactions()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertThenObserveAllRoundTripsEnumsThroughTypeConverters() = runBlocking {
        dao.insert(
            Transaction(
                type = TxnType.CREDIT,
                amountPaise = 120000,
                merchant = "rahul.s",
                category = Category.INCOME,
                sourceText = "HDFC Bank Rs.1,200.00 credited to A/c XX4412 by VPA rahul.s@okhdfc",
                timestamp = 1000L,
            )
        )

        val rows = dao.observeAll().first()

        assertEquals(1, rows.size)
        assertEquals(TxnType.CREDIT, rows[0].type)
        assertEquals(Category.INCOME, rows[0].category)
        assertEquals(120000L, rows[0].amountPaise)
        assertEquals("rahul.s", rows[0].merchant)
    }

    @Test
    fun summarizeOverObserveAllMatchesTheWorkedExample() = runBlocking {
        // The worked example: Rs 850 + Rs 240 + Rs 499 out, Rs 1,200 in.
        dao.insert(Transaction(type = TxnType.DEBIT, amountPaise = 85000, merchant = "Zomato", category = Category.FOOD, sourceText = "", timestamp = 1000L))
        dao.insert(Transaction(type = TxnType.DEBIT, amountPaise = 24000, merchant = "Rapido", category = Category.TRANSPORT, sourceText = "", timestamp = 2000L))
        dao.insert(Transaction(type = TxnType.CREDIT, amountPaise = 120000, merchant = "rahul.s", category = Category.INCOME, sourceText = "", timestamp = 3000L))
        dao.insert(Transaction(type = TxnType.DEBIT, amountPaise = 49900, merchant = "Amazon", category = Category.SHOPPING, sourceText = "", timestamp = 4000L))

        val summary = summarize(dao.observeAll().first())

        assertEquals(158900L, summary.spentPaise)
        assertEquals(120000L, summary.receivedPaise)
        assertEquals(-38900L, summary.netPaise)
    }

    @Test
    fun countSimilarDedupWindowHitsInsideAndMissesOutside() = runBlocking {
        val repo = LedgerRepository(dao)
        val base = 1_700_000_000_000L

        val first = repo.record(
            Transaction(type = TxnType.DEBIT, amountPaise = 85000, merchant = "Zomato", category = Category.FOOD, sourceText = "", timestamp = base)
        )
        assertNotNull(first)

        // 90s later: inside the 2-minute (120s) DEDUP_WINDOW_MS -> treated as the same payment.
        val insideWindow = repo.record(
            Transaction(type = TxnType.DEBIT, amountPaise = 85000, merchant = "Zomato", category = Category.FOOD, sourceText = "", timestamp = base + 90_000L)
        )
        assertNull(insideWindow)

        // 200s after the original: outside the 2-minute window -> a real second payment.
        val outsideWindow = repo.record(
            Transaction(type = TxnType.DEBIT, amountPaise = 85000, merchant = "Zomato", category = Category.FOOD, sourceText = "", timestamp = base + 200_000L)
        )
        assertNotNull(outsideWindow)

        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun observeAllOrdersNewestFirst() = runBlocking {
        dao.insert(Transaction(type = TxnType.DEBIT, amountPaise = 1000, merchant = "A", category = Category.OTHER, sourceText = "", timestamp = 1000L))
        dao.insert(Transaction(type = TxnType.DEBIT, amountPaise = 1000, merchant = "B", category = Category.OTHER, sourceText = "", timestamp = 3000L))
        dao.insert(Transaction(type = TxnType.DEBIT, amountPaise = 1000, merchant = "C", category = Category.OTHER, sourceText = "", timestamp = 2000L))

        val merchantsNewestFirst = dao.observeAll().first().map { it.merchant }

        assertEquals(listOf("B", "C", "A"), merchantsNewestFirst)
    }
}
