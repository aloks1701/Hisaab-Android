package com.aloksharma.hisaab

/**
 * Shoulder-surfing defence. The ledger holds other people's names and how much you paid them,
 * and you will open it on a train, in a shop, on a call being screen-shared.
 *
 * This masks what is DISPLAYED only. Nothing is encrypted and nothing is removed from the
 * database - a private mode that implied at-rest protection it does not provide would be worse
 * than none. ponytail: if the threat model ever becomes someone with the unlocked phone rather
 * than someone glancing at it, this needs real encryption and a biometric gate, not asterisks.
 */
object Privacy {

    /** "EXAMPLE PAYEE" -> "E****** P****". Keeps word count and shape, drops the name. */
    fun maskName(name: String): String = name
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.first() + "*".repeat((word.length - 1).coerceAtLeast(1))
        }
        .ifBlank { "*" }

    /**
     * "₹1,589" -> "₹•,•••". Digits are replaced but grouping and sign survive, so the row still
     * reads as money and the layout does not jump when private mode is toggled.
     */
    fun maskMoney(formatted: String): String = formatted.replace(Regex("[0-9]"), "•")
}
