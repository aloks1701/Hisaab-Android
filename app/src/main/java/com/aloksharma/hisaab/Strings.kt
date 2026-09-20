package com.aloksharma.hisaab

enum class Lang { HI, EN }

/** Hindi is the default. The register is colloquial, the way people actually talk about money. */
data class Strings(
    val tagline: String,
    val spent: String,
    val received: String,
    val net: String,
    val byCategory: String,
    val recent: String,
    val empty: String,
    val grantTitle: String,
    val grantBody: String,
    val grantButton: String,
    val granted: String,
    val simulate: String,
    val needsReview: String,
    val categories: Map<Category, String>,
) {
    companion object {
        fun of(lang: Lang) = if (lang == Lang.HI) HI else EN

        private val HI = Strings(
            tagline = "पेमेंट का हिसाब, अपने आप",
            spent = "खर्च",
            received = "आया",
            net = "नेट बदलाव",
            byCategory = "किस चीज़ पर",
            recent = "हाल के लेन-देन",
            empty = "अभी कुछ नहीं। नोटिफिकेशन की अनुमति दे दीजिए, फिर हर पेमेंट खुद यहाँ आ जाएगी।",
            grantTitle = "नोटिफिकेशन की अनुमति चाहिए",
            grantBody = "हिसाब आपके UPI और बैंक ऐप की नोटिफिकेशन पढ़कर खर्च जोड़ता है। " +
                "सब कुछ फ़ोन में ही रहता है, कहीं नहीं जाता। SMS कभी नहीं पढ़ा जाता।",
            grantButton = "अनुमति दें",
            granted = "अनुमति मिल गई",
            simulate = "डेमो पेमेंट डालें",
            needsReview = "जाँच लें",
            categories = mapOf(
                Category.FOOD to "खाना",
                Category.GROCERIES to "राशन",
                Category.FUEL to "पेट्रोल",
                Category.TRANSPORT to "सफ़र",
                Category.BILLS to "बिल",
                Category.SHOPPING to "खरीदारी",
                Category.HEALTH to "सेहत",
                Category.RENT to "किराया",
                Category.TRANSFER to "ट्रांसफर",
                Category.INCOME to "इनकम",
                Category.OTHER to "बाकी",
            ),
        )

        private val EN = Strings(
            tagline = "Your payments, logged by themselves",
            spent = "Spent",
            received = "Received",
            net = "Net change",
            byCategory = "Where it went",
            recent = "Recent transactions",
            empty = "Nothing yet. Grant notification access and every payment lands here on its own.",
            grantTitle = "Notification access needed",
            grantBody = "Hisaab reads your UPI and bank app notifications to build the ledger. " +
                "Everything stays on this phone. SMS is never read.",
            grantButton = "Grant access",
            granted = "Access granted",
            simulate = "Add demo payment",
            needsReview = "Check",
            categories = mapOf(
                Category.FOOD to "Food",
                Category.GROCERIES to "Groceries",
                Category.FUEL to "Fuel",
                Category.TRANSPORT to "Transport",
                Category.BILLS to "Bills",
                Category.SHOPPING to "Shopping",
                Category.HEALTH to "Health",
                Category.RENT to "Rent",
                Category.TRANSFER to "Transfer",
                Category.INCOME to "Income",
                Category.OTHER to "Other",
            ),
        )
    }
}

/**
 * Track 4: an emulator receives no real bank notifications, so the demo feeds these through
 * the same LedgerRepository.ingest() the live listener uses. Three different source formats
 * so the demo shows the parser is not hardcoded to one app.
 */
val DEMO_NOTIFICATIONS = listOf(
    "Google Pay You paid ₹850 to Zomato",
    "PhonePe Payment of ₹240 to Rapido successful",
    "HDFC Bank Rs.1,200.00 credited to A/c XX4412 by VPA rahul.s@okhdfc on 20-09-26",
    "Paytm ₹499 paid to Amazon using UPI",
    "SBI Rs 320.00 debited from A/c XX7781 to VPA indianoil@ybl on 20-09-26 Ref 5544",
)
