package com.farm_tech.farmhub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberFormatterTest {

    @Test
    fun normalizeKenyanPhone_convertsLocalNumberToInternationalFormat() {
        assertEquals("+254719697174", PhoneNumberFormatter.normalizeKenyanPhone("0719697174"))
    }

    @Test
    fun normalizeKenyanPhone_keepsInternationalNumber() {
        assertEquals("+254719697174", PhoneNumberFormatter.normalizeKenyanPhone("+254719697174"))
    }

    @Test
    fun samePhone_matchesEquivalentFormats() {
        assertTrue(PhoneNumberFormatter.samePhone("0719697174", "+254719697174"))
    }

    @Test
    fun samePhone_rejectsDifferentNumbers() {
        assertFalse(PhoneNumberFormatter.samePhone("0719697174", "0719697175"))
    }
}
