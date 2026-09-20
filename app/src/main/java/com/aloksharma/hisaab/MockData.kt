package com.aloksharma.hisaab

/**
 * A believable month of spending, for demos and screenshots.
 *
 * Never written to the database - the ViewModel swaps this in place of the real flow, so
 * turning demo mode off restores the real ledger untouched. Merchants are well-known brands
 * and the one person-to-person row uses an obviously synthetic name; no real payee, UPI id or
 * account number belongs in here.
 */
object MockData {

    private const val DAY = 24 * 60 * 60 * 1000L

    fun transactions(now: Long = System.currentTimeMillis()): List<Transaction> {
        var id = 0L
        fun row(
            daysAgo: Int, hour: Int, type: TxnType, rupees: Long, merchant: String,
            category: Category, pkg: String, needsReview: Boolean = false,
        ) = Transaction(
            id = ++id,
            type = type,
            amountPaise = rupees * 100,
            merchant = merchant,
            category = category,
            sourceText = "",
            timestamp = now - daysAgo * DAY + hour * 60 * 60 * 1000L,
            needsReview = needsReview,
            sourcePackage = pkg,
        )

        val gpay = "com.google.android.apps.nbu.paisa.user"
        val phonepe = "com.phonepe.app"
        val paytm = "net.one97.paytm"
        val sms = "com.google.android.apps.messaging"

        return listOf(
            row(0, 2, TxnType.DEBIT, 240, "Zomato", Category.FOOD, gpay),
            row(0, 1, TxnType.DEBIT, 60, "Rapido", Category.TRANSPORT, phonepe),
            row(0, 0, TxnType.DEBIT, 1150, "Blinkit", Category.GROCERIES, gpay),
            row(1, 3, TxnType.DEBIT, 499, "Amazon", Category.SHOPPING, paytm),
            row(1, 2, TxnType.DEBIT, 320, "indianoil", Category.FUEL, sms),
            row(1, 1, TxnType.DEBIT, 85, "Chai Point", Category.FOOD, gpay),
            row(2, 4, TxnType.CREDIT, 2500, "A***** K*****", Category.INCOME, sms),
            row(2, 2, TxnType.DEBIT, 749, "Swiggy", Category.FOOD, phonepe),
            row(3, 5, TxnType.DEBIT, 1800, "Airtel", Category.BILLS, gpay),
            row(3, 2, TxnType.DEBIT, 210, "Metro", Category.TRANSPORT, paytm),
            row(4, 3, TxnType.DEBIT, 640, "apollopharmacy", Category.HEALTH, sms),
            row(5, 6, TxnType.DEBIT, 12000, "Landlord", Category.RENT, sms),
            row(5, 2, TxnType.DEBIT, 430, "BigBasket", Category.GROCERIES, gpay),
            row(6, 3, TxnType.DEBIT, 155, "Uber", Category.TRANSPORT, phonepe),
            row(6, 1, TxnType.DEBIT, 300, "R***** S*****", Category.OTHER, sms, needsReview = true),
        )
    }
}
