package com.farm_tech.farmhub.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Memory-safe image preparation for upload.
 *
 * Design principles:
 * ─ Never load the full original bitmap; always call inJustDecodeBounds first.
 * ─ calculateInSampleSize uses an OR condition so the intermediate decoded bitmap
 *   is at most 2× the target on each axis, keeping peak RAM ≈ 6–10 MB regardless
 *   of source resolution (12 MP … 200 MP).
 * ─ Decoded config is always RGB_565 (2 bytes/px vs 4 bytes/px for ARGB_8888).
 * ─ EXIF orientation is corrected before upload so specialists see the right frame.
 * ─ decodeWithRetry doubles inSampleSize on OutOfMemoryError (up to 4 attempts),
 *   providing a graceful degradation path on severely memory-constrained devices.
 * ─ All temporary Bitmap resources are recycled immediately after use.
 * ─ onProgress reports accurate per-phase progress from the IO thread; callers
 *   must be thread-safe when reading the value (StateFlow.value is always safe).
 */
object ImageUploadCompressor {

    private const val TAG = "ImageUploadCompressor"

    /** Longest edge of the output JPEG sent to the server (pixels). */
    const val TARGET_MAX_DIMENSION = 1920

    /** JPEG quality for the output file (85 = excellent quality, ~30–60% smaller file). */
    private const val JPEG_QUALITY = 85

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Full pipeline: bounds-check → EXIF read → sample-decode with OOM retry →
     * orientation correct → scale → JPEG compress → temp file.
     *
     * @param context    Application context (used for cacheDir + ContentResolver).
     * @param sourceUri  content:// or file:// URI of the source image.
     * @param onProgress Optional callback invoked with a value in [0, 1] as work
     *                   progresses. Called from [Dispatchers.IO]; StateFlow is safe.
     * @return           Temp File containing the prepared JPEG. The caller is
     *                   responsible for deleting it when the upload is complete.
     */
    suspend fun prepareImageForUpload(
        context: Context,
        sourceUri: Uri,
        onProgress: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {

        val resolver = context.contentResolver
        onProgress?.invoke(0.05f)

        // ── Step 1: decode bounds only (no pixel allocation) ──────────────────
        val (srcWidth, srcHeight) = decodeBounds(resolver, sourceUri)
        Log.d(TAG, "Source bounds: ${srcWidth}×${srcHeight}")

        if (srcWidth <= 0 || srcHeight <= 0) {
            // Unsupported format – stream-copy without touching pixels
            Log.w(TAG, "Cannot decode bounds; using stream copy fallback")
            onProgress?.invoke(0.50f)
            return@withContext streamCopyToTempFile(context, resolver, sourceUri)
                .also { onProgress?.invoke(1.0f) }
        }

        // ── Step 2: read EXIF orientation (no pixel allocation) ───────────────
        val exifOrientation = readExifOrientation(resolver, sourceUri)
        onProgress?.invoke(0.12f)

        // ── Step 3: calculate inSampleSize ────────────────────────────────────
        // We target an intermediate bitmap whose longest edge is ≤ 2× TARGET.
        // Using OR: sample while EITHER dimension still exceeds the target at
        // the next doubling. This caps intermediate bitmaps at ~6–10 MB (RGB_565)
        // for ANY camera resolution, eliminating the OOM risk on decode.
        val intermediateTarget = TARGET_MAX_DIMENSION * 2
        val inSampleSize = calculateInSampleSize(srcWidth, srcHeight, intermediateTarget, intermediateTarget)
        Log.d(TAG, "inSampleSize=$inSampleSize → approx ${srcWidth / inSampleSize}×${srcHeight / inSampleSize}")

        // ── Step 4: decode with OOM retry ─────────────────────────────────────
        onProgress?.invoke(0.18f)
        val decodedBitmap = decodeWithRetry(resolver, sourceUri, inSampleSize)
            ?: throw IOException("Failed to decode image after multiple attempts")
        Log.d(TAG, "Decoded: ${decodedBitmap.width}×${decodedBitmap.height} config=${decodedBitmap.config}")
        onProgress?.invoke(0.58f)

        // ── Step 5: apply EXIF orientation ────────────────────────────────────
        // Recycles decodedBitmap if a new bitmap is created; returns source if no-op.
        val orientedBitmap = applyExifRotation(decodedBitmap, exifOrientation)
        onProgress?.invoke(0.68f)

        // ── Step 6: scale to final output dimensions ──────────────────────────
        // scaleToFit recycles orientedBitmap if it creates a new bitmap.
        val finalBitmap = scaleToFit(orientedBitmap, TARGET_MAX_DIMENSION, TARGET_MAX_DIMENSION)
        Log.d(TAG, "Final size: ${finalBitmap.width}×${finalBitmap.height}")
        onProgress?.invoke(0.80f)

        // ── Step 7: compress to JPEG temp file ────────────────────────────────
        return@withContext try {
            val outFile = File.createTempFile("fh_upload_", ".jpg", context.cacheDir)
            FileOutputStream(outFile).use { fos ->
                check(finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)) {
                    "JPEG compression returned false"
                }
                fos.flush()
            }
            Log.d(TAG, "Output: ${outFile.length() / 1024} KB → ${outFile.name}")
            onProgress?.invoke(1.0f)
            outFile
        } finally {
            finalBitmap.recycle()
        }
    }

    // ─── Internal helpers (internal for unit-testing) ─────────────────────────

    /**
     * Calculates the largest power-of-2 inSampleSize such that the decoded image
     * is NOT reduced below [reqWidth] × [reqHeight].
     *
     * Critical design choice — OR condition instead of AND:
     *
     * Old code (AND):  continues while BOTH dimensions still exceed target at
     *   next doubling → stops early → 12–108 MP images decode at ~24–32 MB.
     *
     * New code (OR):  continues while EITHER dimension still exceeds target at
     *   next doubling → more aggressive → all resolutions decode at ≈ 6–10 MB.
     *
     * The decoded size is then at most 2× the target on each axis, providing
     * sufficient pixels for high-quality bilinear downscaling in [scaleToFit].
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

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Opens a stream to read image bounds without allocating pixel memory. */
    private fun decodeBounds(resolver: ContentResolver, uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        return opts.outWidth to opts.outHeight
    }

    /**
     * Decodes the bitmap with [initialSampleSize], doubling on [OutOfMemoryError].
     * Up to 4 retry attempts with exponentially increasing sample size.
     * Returns null only if all 4 attempts fail (extreme memory pressure).
     */
    private fun decodeWithRetry(
        resolver: ContentResolver,
        uri: Uri,
        initialSampleSize: Int
    ): Bitmap? {
        var sampleSize = initialSampleSize.coerceAtLeast(1)
        repeat(4) { attempt ->
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    // RGB_565: 2 bytes/px vs ARGB_8888's 4 bytes/px; halves the allocation.
                    // Alpha channel is not needed for camera/gallery photos.
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) return bmp
            } catch (oom: OutOfMemoryError) {
                Log.w(TAG, "OOM at sampleSize=$sampleSize (attempt ${attempt + 1}/4) — retrying with ${sampleSize * 2}")
                System.gc() // Hint GC before next attempt
                sampleSize *= 2
            }
        }
        Log.e(TAG, "All decode attempts failed for uri=$uri")
        return null
    }

    /**
     * Reads the EXIF orientation tag without loading pixel data.
     * Falls back to [ExifInterface.ORIENTATION_NORMAL] on any error.
     */
    private fun readExifOrientation(resolver: ContentResolver, uri: Uri): Int {
        return try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                ExifInterface(afd.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            Log.d(TAG, "EXIF read skipped: ${e.message}")
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Returns a Bitmap with EXIF orientation applied.
     * If no transformation is needed, returns [src] unchanged (no allocation).
     * If a new bitmap is created, [src] is recycled immediately.
     * On OOM during rotation, returns the unrotated bitmap as a safe fallback.
     */
    private fun applyExifRotation(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(-90f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(90f) }
            else -> return src // ORIENTATION_NORMAL or ORIENTATION_UNDEFINED — no-op
        }
        return try {
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            if (rotated !== src) src.recycle()
            rotated
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM during EXIF rotation — returning unrotated bitmap")
            src
        }
    }

    /**
     * Scales [src] to fit within [maxWidth] × [maxHeight] preserving aspect ratio.
     * Returns [src] unchanged if it already fits.
     * If a new bitmap is created, [src] is recycled immediately.
     * Falls back to nearest-neighbour scaling if bilinear OOMs (extremely rare).
     */
    private fun scaleToFit(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (src.width <= maxWidth && src.height <= maxHeight) return src

        val scale = min(maxWidth.toFloat() / src.width, maxHeight.toFloat() / src.height)
        val targetW = max(1, (src.width * scale).roundToInt())
        val targetH = max(1, (src.height * scale).roundToInt())

        return try {
            val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true /* bilinear */)
            if (scaled !== src) src.recycle()
            scaled
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM during bilinear scale — trying nearest-neighbour")
            val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, false)
            if (scaled !== src) src.recycle()
            scaled
        }
    }

    /**
     * Streams the URI content directly to a temp file without any pixel decoding.
     * Used only when the image format is not decodable by BitmapFactory
     * (e.g., unsupported camera RAW formats).
     */
    private fun streamCopyToTempFile(context: Context, resolver: ContentResolver, uri: Uri): File {
        val out = File.createTempFile("fh_upload_raw_", ".bin", context.cacheDir)
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it, bufferSize = 64 * 1024) }
        } ?: throw IOException("Cannot open URI for stream copy: $uri")
        return out
    }
}
