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

    private val AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val CREDIT_WORDS = Regex("""\b(credited|received|deposited|cashback)\b""", RegexOption.IGNORE_CASE)
    private val REFUND_WORDS = Regex("""\b(refund|refunded|reversed)\b""", RegexOption.IGNORE_CASE)
    private val DEBIT_WORDS = Regex("""\b(debited|paid|sent|spent|withdrawn|payment)\b""", RegexOption.IGNORE_CASE)

    private val TO_PARTY = Regex("""\b(?:to|at|towards)\s+(.{2,60})""", RegexOption.IGNORE_CASE)
    private val FROM_PARTY = Regex("""\b(?:from|by)\s+(.{2,60})""", RegexOption.IGNORE_CASE)

    /** "A/c XX1234", "your account", "acct no" — a party connector pointing at an account, not a merchant. */
    private val ACCOUNT_REF = Regex("""^(a/c|ac|acct|account|your\s)""", RegexOption.IGNORE_CASE)

    /** Everything after one of these is transaction metadata, not the merchant name. */
    private val MERCHANT_TAIL = Regex(
        """\s+(?:on|via|using|ref|refno|upi|txn|transaction|id|successful|success|dated|with|is|has|for|in)\b.*""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, timestamp: Long = System.currentTimeMillis()): ParsedTxn? {
        val amountPaise = AMOUNT.find(text)?.groupValues?.get(1)?.let(::toPaise) ?: return null

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
        .trim()
        .trimEnd('.', ',', '!', ';', ':', '-')
        .removePrefix("VPA ").removePrefix("vpa ")
        .substringBefore('@')            // UPI handles: zomato@ybl -> zomato
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
