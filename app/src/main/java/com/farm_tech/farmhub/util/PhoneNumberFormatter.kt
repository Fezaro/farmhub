package com.farm_tech.farmhub.util

object PhoneNumberFormatter {
    fun normalizeKenyanPhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val compact = raw.trim().replace(Regex("[^\\d+]"), "")
        if (compact.isBlank()) return null

        return when {
            compact.startsWith("+254") && compact.length == 13 && compact.drop(1).all { it.isDigit() } -> compact
            compact.startsWith("254") && compact.length == 12 && compact.all { it.isDigit() } -> "+$compact"
            compact.startsWith("0") && compact.length == 10 && compact.all { it.isDigit() } -> "+254${compact.drop(1)}"
            compact.length == 9 && compact.all { it.isDigit() } && compact.startsWith("7") -> "+254$compact"
            else -> null
        }
    }

    fun samePhone(left: String?, right: String?): Boolean {
        val normalizedLeft = normalizeKenyanPhone(left)
        val normalizedRight = normalizeKenyanPhone(right)
        return normalizedLeft != null && normalizedRight != null && normalizedLeft == normalizedRight
    }
}
