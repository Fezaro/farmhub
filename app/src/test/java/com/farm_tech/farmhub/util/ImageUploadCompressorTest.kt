package com.farm_tech.farmhub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ImageUploadCompressor.calculateInSampleSize].
 *
 * The function uses an OR condition (vs the old AND condition) so that the
 * intermediate decoded bitmap stays at most 2× the requested dimensions on
 * EITHER axis.  This keeps peak RAM ≈ 6–10 MB across all camera resolutions
 * (12 MP … 200 MP) instead of the old 24–32 MB.
 */
class ImageUploadCompressorTest {

    // ─── Basic cases ────────────────────────────────────────────────────────

    @Test
    fun calculateInSampleSize_returnsOneWhenImageAlreadySmall() {
        // 1280×720 already fits within 1920×1920 — no sampling needed
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 1280, srcHeight = 720,
            reqWidth = 1920, reqHeight = 1920
        )
        assertEquals(1, sample)
    }

    @Test
    fun calculateInSampleSize_returnsOneForExactMatch() {
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 1920, srcHeight = 1080,
            reqWidth = 1920, reqHeight = 1920
        )
        assertEquals(1, sample)
    }

    // ─── 12 MP (4032×3024) ──────────────────────────────────────────────────

    @Test
    fun calculateInSampleSize_returns2For12MP_withTargetOf1920() {
        // Width / (2*1) = 2016 > 1920 → sample doubles to 2
        // Width / (2*2) = 1008 > 1920? No → stops at 2
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 4032, srcHeight = 3024,
            reqWidth = 1920, reqHeight = 1920
        )
        assertEquals(2, sample)
    }

    // ─── 48 MP (8160×6144) — the key regression case ────────────────────────

    @Test
    fun calculateInSampleSize_returns4For48MP_withTargetOf1920() {
        // Old AND algorithm returned 2 → 4032×3072 intermediate = 24 MB
        // New OR  algorithm returns 4 → 2040×1536 intermediate =  6 MB  ← correct
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 8160, srcHeight = 6144,
            reqWidth = 1920, reqHeight = 1920
        )
        assertEquals(4, sample)
    }

    @Test
    fun calculateInSampleSize_returns2For48MP_withIntermediateTarget() {
        // prepareImageForUpload passes TARGET_MAX_DIMENSION * 2 = 3840 as the request size
        // to allow one extra software-scale step for quality.  For 48 MP + 3840 target:
        // Width / (2*1) = 4080 > 3840 → sample = 2
        // Width / (2*2) = 2040 > 3840? No → stops at 2 → decoded ≈ 4080×3072 = 25 MB
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 8160, srcHeight = 6144,
            reqWidth = 3840, reqHeight = 3840
        )
        assertEquals(2, sample)
    }

    // ─── 108 MP (9248×6936) ─────────────────────────────────────────────────

    @Test
    fun calculateInSampleSize_returns4For108MP_withTargetOf1920() {
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 9248, srcHeight = 6936,
            reqWidth = 1920, reqHeight = 1920
        )
        assertEquals(4, sample)
    }

    // ─── 200 MP (16384×12288) ───────────────────────────────────────────────

    @Test
    fun calculateInSampleSize_returns8For200MP_withTargetOf1920() {
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 16384, srcHeight = 12288,
            reqWidth = 1920, reqHeight = 1920
        )
        assertEquals(8, sample)
    }

    // ─── Invariant: result is always a power of 2 ────────────────────────────

    @Test
    fun calculateInSampleSize_alwaysReturnsPowerOfTwo() {
        val testCases = listOf(
            Triple(4032, 3024, 1920),   // 12 MP
            Triple(8064, 6048, 1920),   // 50 MP
            Triple(9248, 6936, 1920),   // 108 MP
            Triple(16384, 12288, 1920), // 200 MP
            Triple(1920, 1080, 1920),   // exact fit
            Triple(640, 480, 1920)      // smaller than target
        )
        for ((w, h, req) in testCases) {
            val s = ImageUploadCompressor.calculateInSampleSize(w, h, req, req)
            val isPowerOfTwo = s > 0 && (s and (s - 1)) == 0
            assertTrue("Expected power-of-2 for ${w}×${h} req=${req} but got $s", isPowerOfTwo)
        }
    }

    // ─── Invariant: decoded size ≤ 2× req on each axis ───────────────────────

    @Test
    fun calculateInSampleSize_decodedSizeIsWithin2xTarget() {
        val megapixelCases = listOf(
            Triple(4032, 3024, 1920),
            Triple(8064, 6048, 1920),
            Triple(9248, 6936, 1920),
            Triple(16384, 12288, 1920)
        )
        for ((w, h, req) in megapixelCases) {
            val s = ImageUploadCompressor.calculateInSampleSize(w, h, req, req)
            val decodedW = w / s
            val decodedH = h / s
            assertTrue(
                "Decoded width $decodedW should be ≤ 2×$req for ${w}×${h} sampleSize=$s",
                decodedW <= req * 2
            )
            assertTrue(
                "Decoded height $decodedH should be ≤ 2×$req for ${w}×${h} sampleSize=$s",
                decodedH <= req * 2
            )
        }
    }
}

