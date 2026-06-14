package com.farm_tech.farmhub.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUploadCompressorTest {

    @Test
    fun calculateInSampleSize_returnsPowerOfTwoForLargeImage() {
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 8160,
            srcHeight = 6144,
            reqWidth = 1920,
            reqHeight = 1920
        )

        assertEquals(2, sample)
    }

    @Test
    fun calculateInSampleSize_returnsOneWhenImageAlreadySmall() {
        val sample = ImageUploadCompressor.calculateInSampleSize(
            srcWidth = 1280,
            srcHeight = 720,
            reqWidth = 1920,
            reqHeight = 1920
        )

        assertEquals(1, sample)
    }
}

