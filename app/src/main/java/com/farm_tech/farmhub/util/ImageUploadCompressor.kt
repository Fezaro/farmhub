package com.farm_tech.farmhub.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prepares local images for upload without manually decoding image streams.
 * Coil owns loading, downsampling, caching, and EXIF orientation handling.
 */
object ImageUploadCompressor {
    private const val TAG = "ImageUploadCompressor"
    const val TARGET_MAX_DIMENSION = 1920
    private const val JPEG_QUALITY = 85

    suspend fun prepareImageForUpload(
        context: Context,
        sourceUri: Uri,
        onProgress: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        onProgress?.invoke(0.05f)
        val decodedBitmap = loadWithCoil(context, sourceUri)
        if (decodedBitmap == null) {
            Log.w(TAG, "Coil could not decode image; using stream-copy fallback")
            onProgress?.invoke(0.50f)
            return@withContext streamCopyToTempFile(context, context.contentResolver, sourceUri)
                .also { onProgress?.invoke(1.0f) }
        }

        onProgress?.invoke(0.58f)
        val finalBitmap = scaleToFit(decodedBitmap, TARGET_MAX_DIMENSION, TARGET_MAX_DIMENSION)
        onProgress?.invoke(0.80f)

        return@withContext try {
            val outFile = File.createTempFile("fh_upload_", ".jpg", context.cacheDir)
            FileOutputStream(outFile).use { output ->
                check(finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "JPEG compression returned false"
                }
                output.flush()
            }
            onProgress?.invoke(1.0f)
            outFile
        } finally {
            finalBitmap.recycle()
        }
    }

    private suspend fun loadWithCoil(context: Context, sourceUri: Uri): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(sourceUri)
            .size(TARGET_MAX_DIMENSION, TARGET_MAX_DIMENSION)
            .allowHardware(false)
            .allowRgb565(true)
            .build()
        val result = context.imageLoader.execute(request)
        (result as? SuccessResult)?.drawable?.toBitmap(config = Bitmap.Config.RGB_565)
    } catch (error: Exception) {
        Log.w(TAG, "Coil image load failed", error)
        null
    }

    /**
     * Retained for regression tests and callers that need a power-of-two sample
     * calculation. Coil performs the corresponding bounded decode internally.
     */
    internal fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (srcWidth <= reqWidth && srcHeight <= reqHeight) return 1
        var inSampleSize = 1
        while (
            srcWidth / (inSampleSize * 2) > reqWidth ||
            srcHeight / (inSampleSize * 2) > reqHeight
        ) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun scaleToFit(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (src.width <= maxWidth && src.height <= maxHeight) return src

        val scale = min(maxWidth.toFloat() / src.width, maxHeight.toFloat() / src.height)
        val targetWidth = max(1, (src.width * scale).roundToInt())
        val targetHeight = max(1, (src.height * scale).roundToInt())
        return try {
            Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true).also { scaled ->
                if (scaled !== src) src.recycle()
            }
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Bilinear scale failed; using nearest-neighbour scaling")
            Bitmap.createScaledBitmap(src, targetWidth, targetHeight, false).also { scaled ->
                if (scaled !== src) src.recycle()
            }
        }
    }

    private fun streamCopyToTempFile(context: Context, resolver: ContentResolver, uri: Uri): File {
        val out = File.createTempFile("fh_upload_raw_", ".bin", context.cacheDir)
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it, bufferSize = 64 * 1024) }
        } ?: throw IOException("Cannot open URI for stream copy: $uri")
        return out
    }
}
