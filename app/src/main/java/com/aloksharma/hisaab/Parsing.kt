package com.aloksharma.hisaab

data class ParsedTxn(
    val type: TxnType,
    val amountPaise: Long,
    val merchant: String,
    val category: Category,
    val needsReview: Boolean,
)

/**
 * Format-agnostic rather than per-app: match the amount, the direction word and the
 * party connector ("to X" / "from X") wherever they appear.
 *
 * ponytail: tuned against the notification shapes GPay/PhonePe/Paytm/bank apps commonly
 * post, not against captured samples. Real samples are still needed to confirm coverage —
 * add failing cases to ParsingTest and widen the regexes, don't rewrite the approach.
 */
object NotificationParser {

    // ponytail: currency-then-number only ("₹850", "Rs.850", "INR 850"), matching every real
    // bank/UPI format seen. Amount-before-currency ("850 INR") is not supported — speculative,
    // no sample confirms it's needed, and it would widen the false-positive surface for bare
    // numbers. Add it if real captured text shows the pattern.
    // ponytail: ASCII/English keywords only, so a fully Hindi/Devanagari notification (own
    // direction words, no "paid"/"credited") won't match and is silently dropped (not a false
    // positive — just never becomes a row). Guessing Hindi keyword translations without a real
    // sample risks worse false positives than the gap it closes; wait for a captured sample.
    private val AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    // "cashback" deliberately excluded: promo copy ("Get ₹500 cashback when you pay with UPI")
    // says "cashback" without "credited", while a real cashback payout notification almost
    // always also says "credited"/"received". Dropping the word trades a few missed
    // cashback-only credits for not recording promotional offers as income.
    // ponytail: if real samples show banks confirming cashback without "credited"/"received",
    // bring the word back and gate it on the absence of offer language ("get", "upto", "avail") instead.
    private val CREDIT_WORDS = Regex("""\b(credited|received|deposited)\b""", RegexOption.IGNORE_CASE)
    // Checked before CREDIT_WORDS: a refund notification usually also says "credited"
    // ("₹499 refund credited to your account"), and REFUND is the more precise type.
    private val REFUND_WORDS = Regex("""\b(refund|refunded|reversed)\b""", RegexOption.IGNORE_CASE)
    private val DEBIT_WORDS = Regex("""\b(debited|paid|sent|spent|withdrawn|payment)\b""", RegexOption.IGNORE_CASE)

    // An OTP/authorization request quotes the amount and merchant of a payment that has not
    // happened yet ("...authorize payment of Rs.5000 to Amazon Pay. OTP is 445566...") — it
    // would otherwise parse as a completed debit. Real completed-transaction notifications
    // essentially never mention OTP, so this is a one-sided, safe guard.
    // ponytail: blanket reject on the word "OTP" / "one time password". If a bank ever echoes
    // OTP in a post-facto confirmation this drops a real row — no sample shows that yet.
    private val OTP_WORDS = Regex("""\bOTP\b|one[\s-]?time\s+password""", RegexOption.IGNORE_CASE)

    private val TO_PARTY = Regex("""\b(?:to|at|towards)\s+(.{2,60})""", RegexOption.IGNORE_CASE)
    private val FROM_PARTY = Regex("""\b(?:from|by)\s+(.{2,60})""", RegexOption.IGNORE_CASE)

    /** "A/c XX1234", "your account", "acct no" — a party connector pointing at an account, not a merchant. */
    private val ACCOUNT_REF = Regex("""^(a/c|ac|acct|account|your\s)""", RegexOption.IGNORE_CASE)

    /**
     * Everything after one of these is transaction metadata, not the merchant name.
     *
     * ponytail: these are also plain English words that show up inside real merchant names
     * ("Cafe on Wheels", "Food For All", "Pay It Forward") — trimming those false-positives
     * would mean dropping "on"/"for"/"in" from the list, which would then let genuine trailing
     * metadata ("... to Big Bazaar on 20-09-26 Ref 5544") leak into the merchant instead. A
     * word list can't tell "on" the connector from "on" the business name apart. Accepted as
     * a known ceiling — the category still resolves correctly off the surviving substring in
     * both directions, only the display name is occasionally truncated or noisy. Only a real
     * corpus (to tell which reading is more common) or per-app parsing (ruled out) fixes this.
     */
    private val MERCHANT_TAIL = Regex(
        """\s+(?:on|via|using|ref|refno|upi|txn|transaction|id|successful|success|dated|with|is|has|for|in)\b.*""",
        RegexOption.IGNORE_CASE,
    )

    /** A bare 6+ digit trailing token is a UPI/txn reference glued on with no keyword, not part
     * of the merchant name ("to Swiggy 430281999456"). Real merchant names don't end this way;
     * short trailing numbers (store/branch numbers) are left alone. */
    private val TRAILING_REF_NUMBER = Regex("""\s+\d{6,}$""")

    fun parse(text: String, timestamp: Long = System.currentTimeMillis()): ParsedTxn? {
        if (OTP_WORDS.containsMatchIn(text)) return null

        val amountPaise = AMOUNT.find(text)?.groupValues?.get(1)?.let(::toPaise) ?: return null
        // A ₹0 notification is a glitch or a non-payment alert, never a real ledger row.
        if (amountPaise <= 0L) return null

        val type = when {
            REFUND_WORDS.containsMatchIn(text) -> TxnType.REFUND
            CREDIT_WORDS.containsMatchIn(text) -> TxnType.CREDIT
            DEBIT_WORDS.containsMatchIn(text) -> TxnType.DEBIT
            else -> return null
        }

        val connector = if (type == TxnType.DEBIT) TO_PARTY else FROM_PARTY
        val merchant = connector.findAll(text)
            .map { cleanMerchant(it.groupValues[1]) }
            .firstOrNull { it.isNotBlank() && !ACCOUNT_REF.containsMatchIn(it) }

        // No counterparty means this probably isn't a payment notification at all.
        if (merchant == null) return null

        return ParsedTxn(
            type = type,
            amountPaise = amountPaise,
            merchant = merchant,
            category = Categorizer.categorize(merchant, type),
            // OTHER means no keyword matched, so the guess is weak — surface it for a human to fix.
            needsReview = Categorizer.categorize(merchant, type) == Category.OTHER,
        )
    }

    /** "1,200.50" -> 120050. Rupee-only amounts get their two paise digits appended. */
    internal fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "")
        val parts = cleaned.split(".")
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = when (parts.size) {
            1 -> 0L
            2 -> parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: return null
            else -> return null
        }
        return rupees * 100 + paise
    }

    private fun cleanMerchant(raw: String): String = raw
        .replace(MERCHANT_TAIL, "")
        .substringBefore('\n')
        .substringBefore(',')            // ", avl bal Rs.4,200" / ", balance Rs.4,200" etc.
        .trim()
        .trimEnd('.', ',', '!', ';', ':', '-')
        .removePrefix("VPA ").removePrefix("vpa ")
        .substringBefore('@')            // UPI handles: zomato@ybl -> zomato
        .replace(TRAILING_REF_NUMBER, "")
        .trim()
}

object Categorizer {

    private val KEYWORDS: List<Pair<Category, List<String>>> = listOf(
        Category.FOOD to listOf("zomato", "swiggy", "restaurant", "cafe", "dhaba", "pizza", "domino",
            "mcdonald", "kfc", "burger", "starbucks", "chai", "bakery", "biryani", "food", "eatery"),
        Category.GROCERIES to listOf("bigbasket", "blinkit", "zepto", "dmart", "instamart", "grofer",
            "kirana", "grocery", "supermarket", "reliance fresh", "sabzi"),
        Category.FUEL to listOf("indian oil", "indianoil", "iocl", "bharat petroleum", "bpcl", "hpcl",
            "petrol", "diesel", "fuel", "shell"),
        Category.TRANSPORT to listOf("uber", "ola", "rapido", "auto", "metro", "irctc", "railway",
            "redbus", "taxi", "cab", "toll", "fastag", "parking", "bus"),
        Category.BILLS to listOf("electricity", "airtel", "jio", "vodafone", "bsnl", "broadband",
            "recharge", "gas", "water", "dth", "tata power", "adani", "bescom", "bill"),
        Category.SHOPPING to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma",
            "decathlon", "lifestyle", "mall", "store"),
        Category.HEALTH to listOf("pharmacy", "apollo", "medplus", "1mg", "pharmeasy", "hospital",
            "clinic", "doctor", "medical", "diagnostic", "pathlab"),
        Category.RENT to listOf("rent", "landlord", "maintenance", "society"),
    )

    fun categorize(merchant: String, type: TxnType): Category {
        val needle = merchant.lowercase()
        KEYWORDS.forEach { (category, words) ->
            if (words.any { needle.contains(it) }) return category
        }
        return when (type) {
            TxnType.CREDIT -> Category.INCOME
            TxnType.REFUND -> Category.OTHER
            else -> Category.OTHER
        }
    }
}
