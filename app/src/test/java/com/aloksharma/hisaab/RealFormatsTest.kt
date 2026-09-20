package com.aloksharma.hisaab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formats captured from a real phone rather than invented.
 *
 * Every one of these is a real notification shape observed in the wild, with the payee name,
 * account digits and reference number replaced by synthetic values. Never paste a real name,
 * UPI ID, account number or transaction reference into this file.
 *
 * The capture path these come from is worth understanding: the bank sends an SMS, the SMS app
 * posts a notification for it, and the listener reads that notification. The app never holds
 * RECEIVE_SMS or READ_SMS.
 */
class RealFormatsTest {

    /** HDFC UPI debit, as relayed by the SMS app. Observed 2026-09-20 on Android 16. */
    private val hdfcUpiDebit = """
        VM-HDFCBK-T Sent Rs.1.00
        From HDFC Bank A/C *1234
        To EXAMPLE PAYEE
        On 20/09/26
        Ref 100000000000
        Not You?
        Call 18002586161/SMS BLOCK UPI to 7308080808
    """.trimIndent()

    @Test
    fun `parses a real HDFC UPI debit relayed through the SMS app`() {
        val parsed = NotificationParser.parse(hdfcUpiDebit)!!
        assertEquals(TxnType.DEBIT, parsed.type)
        assertEquals(100L, parsed.amountPaise)
        assertEquals("EXAMPLE PAYEE", parsed.merchant)
    }

    @Test
    fun `a person-to-person transfer has no category keyword, so it is flagged for review`() {
        val parsed = NotificationParser.parse(hdfcUpiDebit)!!
        assertEquals(Category.OTHER, parsed.category)
        assertTrue(parsed.needsReview)
    }

    @Test
    fun `the payee name does not keep the padding the bank SMS puts in it`() {
        val padded = hdfcUpiDebit.replace("To EXAMPLE PAYEE", "To EXAMPLE  PAYEE")
        assertEquals("EXAMPLE PAYEE", NotificationParser.parse(padded)!!.merchant)
    }

    @Test
    fun `the bank is recovered from the SMS sender id, not the messaging app package`() {
        assertEquals("HDFC", SourceApp.label("com.google.android.apps.messaging", hdfcUpiDebit))
        assertEquals("ICICI", SourceApp.label("com.google.android.apps.messaging", "JD-ICICIB-S Rs.50 debited to Zomato"))
    }

    @Test
    fun `an unrecognised sender id still labels the row rather than leaving it blank`() {
        assertEquals("SMS", SourceApp.label("com.google.android.apps.messaging", "XX-NOSUCH-T Rs.50 paid to Zomato"))
        assertEquals("SMS", SourceApp.label("com.google.android.apps.messaging", "no sender id at all Rs.50 paid to Zomato"))
    }

    @Test
    fun `a payment app package still labels from the package, not the text`() {
        assertEquals("PhonePe", SourceApp.label("com.phonepe.app", "Payment of Rs.240 to Rapido successful"))
        assertEquals(null, SourceApp.label("com.unknown.app", "Rs.240 paid to Rapido"))
    }
}

/** The ledger must not become a copy of the user's messages. */
class StoredTextTest {

    private val bankSms = """
        VM-HDFCBK-T Sent Rs.1.00
        From HDFC Bank A/C *1234
        To EXAMPLE PAYEE
        Ref 100000000000
    """.trimIndent()

    @Test
    fun `only the sender id is extracted, never the message body`() {
        val kept = SourceApp.senderId(bankSms)
        assertEquals("VM-HDFCBK-T", kept)
        assertFalse(kept!!.contains("EXAMPLE"))
        assertFalse(kept.contains("1234"))
        assertFalse(kept.contains("100000000000"))
    }

    @Test
    fun `a notification with no sender id keeps nothing at all`() {
        assertEquals(null, SourceApp.senderId("Google Pay You paid Rs.850 to Zomato"))
    }

    @Test
    fun `the bank is still recoverable from the stored sender id alone`() {
        val stored = SourceApp.senderId(bankSms)!!
        assertEquals("HDFC", SourceApp.label("com.google.android.apps.messaging", stored))
    }
}
