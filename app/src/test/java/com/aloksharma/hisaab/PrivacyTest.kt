package com.aloksharma.hisaab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrivacyTest {

    @Test
    fun `masks every word of a name but keeps its shape`() {
        assertEquals("E****** P****", Privacy.maskName("EXAMPLE PAYEE"))
        assertEquals("Z*****", Privacy.maskName("Zomato"))
    }

    @Test
    fun `survives degenerate names without leaking or crashing`() {
        assertEquals("*", Privacy.maskName(""))
        assertEquals("*", Privacy.maskName("   "))
        assertEquals("A*", Privacy.maskName("A"))
        assertEquals("A* B*", Privacy.maskName("A  B"))
    }

    @Test
    fun `masks digits but keeps currency, grouping and sign`() {
        assertEquals("₹•,•••", Privacy.maskMoney("₹1,589"))
        assertEquals("-₹•••", Privacy.maskMoney("-₹389"))
        assertEquals("₹•,•••.••", Privacy.maskMoney("₹1,200.50"))
    }

    @Test
    fun `no digit survives masking`() {
        val masked = Privacy.maskMoney(formatPaise(123456789))
        assertFalse(masked.any { it.isDigit() })
    }
}
