package com.farm_tech.farmhub.network

object MediaRetryPolicy {
    fun delayMs(code: Int, attempt: Int, retryAfterSeconds: Long? = null): Long? {
        if (attempt !in 0..1 || code !in setOf(408, 429, 500, 502, 503, 504)) return null
        if (retryAfterSeconds != null && retryAfterSeconds > 30) return null
        val delay = maxOf(800L shl attempt, (retryAfterSeconds ?: 0).coerceAtLeast(0) * 1000L)
        // Long throttles should return control to the user, not freeze loading.
        return delay.takeIf { it <= 30_000L }
    }
}
