package com.farm_tech.farmhub.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUploadCompressor {
    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1920
    private const val JPEG_QUALITY = 82

    suspend fun prepareImageForUpload(context: Context, sourceUri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = decodeBounds(resolver, sourceUri)

        // If the source type cannot be decoded as bitmap bounds, still stream-copy to avoid crashes.
        if (bounds.first <= 0 || bounds.second <= 0) {
            return@withContext copyUriToTempFile(context, resolver, sourceUri)
        }

        val sampleSize = calculateInSampleSize(
            srcWidth = bounds.first,
            srcHeight = bounds.second,
            reqWidth = MAX_WIDTH,
            reqHeight = MAX_HEIGHT
        )

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val decodedBitmap = resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IOException("Unable to decode image stream")

        val scaledBitmap = try {
            scaleIfNeeded(decodedBitmap, MAX_WIDTH, MAX_HEIGHT)
        } catch (oom: OutOfMemoryError) {
            decodedBitmap.recycle()
            throw oom
        }

        if (scaledBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        return@withContext try {
            val file = File.createTempFile("post_upload_", ".jpg", context.cacheDir)
            FileOutputStream(file).use { output ->
                if (!scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IOException("Failed to compress image")
                }
            }
            file
        } finally {
            scaledBitmap.recycle()
        }
    }

    private fun decodeBounds(resolver: ContentResolver, sourceUri: Uri): Pair<Int, Int> {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        } ?: throw IOException("Unable to open source image")

        return boundsOptions.outWidth to boundsOptions.outHeight
    }

    private fun scaleIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap

        val widthRatio = maxWidth.toFloat() / bitmap.width.toFloat()
        val heightRatio = maxHeight.toFloat() / bitmap.height.toFloat()
        val ratio = minOf(widthRatio, heightRatio)

        val targetWidth = max(1, (bitmap.width * ratio).roundToInt())
        val targetHeight = max(1, (bitmap.height * ratio).roundToInt())

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun copyUriToTempFile(context: Context, resolver: ContentResolver, sourceUri: Uri): File {
        val file = File.createTempFile("post_upload_", ".jpg", context.cacheDir)
        resolver.openInputStream(sourceUri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open source image")
        return file
    }

    internal fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}


