package com.aloksharma.hisaab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Edge cases and false-positive hardening for [NotificationParser]. Every case here was
 * verified against the actual regex behavior before being asserted — some turned out to
 * already be correct (asserted as regression guards), some were real bugs (fixed in
 * Parsing.kt, asserted as the corrected behavior), and some are accepted limitations
 * (asserted as their current, imperfect-but-documented behavior — see the matching
 * `ponytail:` comment in Parsing.kt for the ceiling and upgrade path).
 */
class ParsingEdgeTest {

    // ---- False positives: promotional / offer copy ----------------------------------------

    @Test
    fun `promo cashback offer with no completed-transaction word is not a payment`() {
        // Already correct before this change: no "from"/"by" connector for FROM_PARTY to grab.
        assertNull(NotificationParser.parse("Get ₹500 cashback when you pay with UPI"))
    }

    @Test
    fun `promo cashback offer with a from-clause was a real false positive, now rejected`() {
        // Before: "cashback" alone triggered CREDIT, and "from PayZapp" gave it a merchant,
        // recording an offer as ₹500 of real income. Fixed by dropping "cashback" from
        // CREDIT_WORDS (see the ponytail comment on it) since real cashback *payouts* also
        // say "credited"/"received".
        assertNull(NotificationParser.parse("Flat ₹500 cashback from PayZapp on bill payments above ₹2000"))
    }

    @Test
    fun `promo price mention with no counterparty is not a payment`() {
        assertNull(NotificationParser.parse("₹99 paid plans, upgrade now"))
    }

    @Test
    fun `order-status notification with no amount is not a payment`() {
        assertNull(NotificationParser.parse("Your Swiggy order from Domino's is on the way"))
    }

    @Test
    fun `chat message quoting an amount has no payment connector`() {
        assertNull(NotificationParser.parse("Rahul: paid you Rs 500 already?"))
    }

    @Test
    fun `balance alert has no direction word`() {
        assertNull(NotificationParser.parse("Your A/c XX1234 balance is Rs.4,200"))
    }

    // ---- False positive: OTP / authorization requests --------------------------------------

    @Test
    fun `OTP request for a not-yet-completed payment is rejected`() {
        // Before: "payment" + "to Amazon Pay" parsed this as a completed ₹5000 debit, even
        // though the money hasn't moved yet — the user hasn't entered the OTP.
        val text = "Do not share your OTP 445566 with anyone. It will be used to authorize " +
            "payment of Rs.5000 to Amazon Pay via UPI. OTP is valid for 10 mins."
        assertNull(NotificationParser.parse(text))
    }

    @Test
    fun `one-time-password spelled out is also rejected`() {
        assertNull(NotificationParser.parse("Your one time password is 1234 to pay Rs.500 to Zomato"))
    }

    // ---- False positive: degenerate amounts -------------------------------------------------

    @Test
    fun `a zero-rupee notification is not recorded`() {
        assertNull(NotificationParser.parse("SBI Rs.0.00 debited to Zomato"))
    }

    @Test
    fun `a zero-rupee notification with paise-only amount is not recorded`() {
        assertNull(NotificationParser.parse("SBI Rs.0 debited to Zomato"))
    }

    // ---- Indian digit grouping ---------------------------------------------------------------

    @Test
    fun `lakh-style grouping already converts correctly - comma stripping is grouping-agnostic`() {
        // toPaise strips every comma regardless of where it falls, so 3-3-3 (western) and
        // 3-2-2 (Indian lakh/crore) grouping both come out right. Not a bug, just confirming.
        assertEquals(10000000L, NotificationParser.toPaise("1,00,000"))
        assertEquals(1234567800L, NotificationParser.toPaise("1,23,45,678"))
    }

    @Test
    fun `lakh amount parses correctly end to end`() {
        val parsed = NotificationParser.parse("Google Pay You paid ₹1,00,000 to Landlord")!!
        assertEquals(10000000L, parsed.amountPaise)
        assertEquals(Category.RENT, parsed.category)
    }

    // ---- Multiple amounts in one notification -------------------------------------------------

    @Test
    fun `paid-with-no-counterparty balance mention is not recorded regardless of second amount`() {
        assertNull(NotificationParser.parse("₹850 paid, balance Rs.4,200"))
    }

    @Test
    fun `the payment amount wins over a trailing balance figure, and balance text leaves the merchant`() {
        // Before: merchant came out as "Zomato, avl bal Rs.4,200.00" — the trailing balance
        // clause leaked in because none of its words were in the MERCHANT_TAIL stopword list.
        // Fixed by cutting the merchant at the first comma, since a comma-separated clause
        // after a payment notification's merchant is metadata, not part of the name.
        val parsed = NotificationParser.parse("PhonePe: Rs.850 paid to Zomato, avl bal Rs.4,200.00")!!
        assertEquals(85000L, parsed.amountPaise)
        assertEquals("Zomato", parsed.merchant)
    }

    // ---- Amount formats ------------------------------------------------------------------------

    @Test
    fun `INR prefix with a space already works`() {
        val parsed = NotificationParser.parse("Paytm INR 1200 paid to Amazon")!!
        assertEquals(120000L, parsed.amountPaise)
    }

    @Test
    fun `Rs with no space before the digits already works`() {
        val parsed = NotificationParser.parse("Paytm Rs1200 paid to Amazon")!!
        assertEquals(120000L, parsed.amountPaise)
    }

    @Test
    fun `rupee symbol with extra space already works`() {
        val parsed = NotificationParser.parse("GPay Paid ₹ 850 to Zomato")!!
        assertEquals(85000L, parsed.amountPaise)
    }

    @Test
    fun `amount before the currency token is not supported - accepted, see ponytail comment on AMOUNT`() {
        // No sample confirms banks actually post this shape; supporting it would widen the
        // false-positive surface for bare numbers near the word "INR"/"Rs" elsewhere in text.
        assertNull(NotificationParser.parse("Paid 850 INR to Zomato"))
    }

    // ---- Direction ambiguity ---------------------------------------------------------------------

    @Test
    fun `a refund that also says credited is classified as a refund, not a credit`() {
        val parsed = NotificationParser.parse("Refund of ₹499 credited to your account from Amazon")!!
        assertEquals(TxnType.REFUND, parsed.type)
        assertEquals("Amazon", parsed.merchant)
    }

    @Test
    fun `debited-then-refunded in one notification is classified as a refund`() {
        val parsed = NotificationParser.parse("₹200 debited, ₹200 refunded to your account from Zomato")!!
        assertEquals(TxnType.REFUND, parsed.type)
        assertEquals(20000L, parsed.amountPaise)
        assertEquals("Zomato", parsed.merchant)
    }

    // ---- Merchant extraction ------------------------------------------------------------------------

    @Test
    fun `merchant name containing the stopword 'on' is truncated - accepted limitation`() {
        // "Cafe on Wheels" comes out as just "Cafe". See the ponytail comment on MERCHANT_TAIL:
        // the same word "on" is needed to cut real trailing dates ("... to Big Bazaar on
        // 20-09-26"), so it can't be removed from the stopword list without trading one bug
        // for a worse one. Category still resolves correctly ("cafe" keyword still matches).
        val parsed = NotificationParser.parse("Google Pay You paid ₹150 to Cafe on Wheels using UPI")!!
        assertEquals("Cafe", parsed.merchant)
        assertEquals(Category.FOOD, parsed.category)
    }

    @Test
    fun `merchant name containing the stopword 'for' is truncated - accepted limitation`() {
        val parsed = NotificationParser.parse("Paytm ₹200 paid to Food For All using UPI")!!
        assertEquals("Food", parsed.merchant)
    }

    @Test
    fun `merchant name where the stopword is glued to a longer word survives intact`() {
        // "Forward" doesn't match \bfor\b because there's no word boundary between "for" and
        // "ward" — this one happens to come through whole, unlike "Food For All".
        val parsed = NotificationParser.parse("Google Pay You paid ₹300 to Pay It Forward")!!
        assertEquals("Pay It Forward", parsed.merchant)
    }

    @Test
    fun `all-caps card-swipe merchant strings still categorize correctly`() {
        val parsed = NotificationParser.parse(
            "Rs.450.00 spent at AMAZON PAY INDIA PVT LTD using Card XX1234"
        )!!
        assertEquals("AMAZON PAY INDIA PVT LTD", parsed.merchant)
        assertEquals(Category.SHOPPING, parsed.category)
    }

    @Test
    fun `a trailing reference number with no keyword no longer pollutes the merchant`() {
        // Before: merchant came out as "Swiggy 430281999456" — nothing in MERCHANT_TAIL
        // catches a bare digit blob with no preceding keyword like "Ref"/"txn id".
        val parsed = NotificationParser.parse("Google Pay You paid ₹500 to Swiggy 430281999456")!!
        assertEquals("Swiggy", parsed.merchant)
    }

    @Test
    fun `a short trailing number is left alone, it is not mistaken for a reference id`() {
        val parsed = NotificationParser.parse("Google Pay You paid ₹500 to Store 4521")!!
        assertEquals("Store 4521", parsed.merchant)
    }

    @Test
    fun `a decoy 'to your account' clause is skipped in favor of the real counterparty`() {
        val parsed = NotificationParser.parse("SBI Rs.500 debited to your account\nto Ramesh Kumar successful")!!
        assertEquals(TxnType.DEBIT, parsed.type)
        assertEquals("Ramesh Kumar", parsed.merchant)
    }

    // ---- Hindi / Devanagari text ---------------------------------------------------------------

    @Test
    fun `a fully Hindi notification is silently dropped, not misparsed`() {
        // Direction words (DEBIT_WORDS etc.) and connectors (TO_PARTY/FROM_PARTY) are English
        // only. A Hindi notification has no English direction word to match, so this returns
        // null rather than a wrong row. See the ponytail comment on AMOUNT: this is an
        // accepted gap, not guessed at, until a real Hindi sample is captured.
        assertNull(NotificationParser.parse("आपके खाते से ₹500 डेबिट हुआ, प्राप्तकर्ता: Zomato"))
    }

    // ---- Degenerate input ------------------------------------------------------------------------

    @Test
    fun `empty string does not crash and is not a payment`() {
        assertNull(NotificationParser.parse(""))
    }

    @Test
    fun `whitespace-only string is not a payment`() {
        assertNull(NotificationParser.parse("\n\n   \n"))
    }

    @Test
    fun `a very long string with no payment pattern returns null quickly`() {
        assertNull(NotificationParser.parse("x".repeat(50_000)))
    }

    @Test
    fun `a valid payment still parses correctly even behind a very long prefix`() {
        val text = "log spam ".repeat(5_000) + "Google Pay You paid ₹850 to Zomato"
        val parsed = NotificationParser.parse(text)!!
        assertEquals(85000L, parsed.amountPaise)
        assertEquals("Zomato", parsed.merchant)
    }

    @Test
    fun `merchant on its own trailing line is captured cleanly`() {
        val parsed = NotificationParser.parse("Google Pay\nYou paid ₹850\nto Zomato")!!
        assertEquals("Zomato", parsed.merchant)
    }

    @Test
    fun `a decoy connector on a later line does not override the real merchant`() {
        val parsed = NotificationParser.parse("Google Pay You paid ₹850 to Zomato\nTap to view details")!!
        assertEquals("Zomato", parsed.merchant)
    }

    @Test
    fun `money still never goes through floating point for these new cases`() {
        assertEquals(10000000L, NotificationParser.toPaise("1,00,000"))
        assertEquals(0L, NotificationParser.toPaise("0"))
        assertEquals(0L, NotificationParser.toPaise("0.00"))
    }
}
