package com.farm_tech.farmhub.network

import org.junit.Assert.*
import org.junit.Test

class MediaRetryPolicyTest {
    @Test fun retriesAreBoundedAndRespectServerDelay() {
        assertEquals(800L, MediaRetryPolicy.delayMs(503, 0))
        assertEquals(5000L, MediaRetryPolicy.delayMs(429, 1, 5))
        assertNull(MediaRetryPolicy.delayMs(503, 2))
        assertNull(MediaRetryPolicy.delayMs(429, 0, 60))
    }
    @Test fun neverRetryAuthorizationOrSuccessfulResponses() {
        listOf(200, 400, 401, 403, 404).forEach { assertNull(MediaRetryPolicy.delayMs(it, 0)) }
    }
}
