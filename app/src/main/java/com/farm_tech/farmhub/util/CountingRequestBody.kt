package com.farm_tech.farmhub.util

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * OkHttp [RequestBody] decorator that tracks upload progress.
 *
 * Wraps any [RequestBody] and invokes [onProgress] after each chunk is written
 * to the underlying sink. This allows the UI to display real network-upload progress
 * rather than a fake stepped value.
 *
 * Usage:
 * ```kotlin
 * val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
 * val countingBody = CountingRequestBody(body) { written, total ->
 *     val fraction = if (total > 0) written.toFloat() / total else 0f
 *     // update StateFlow or callback
 * }
 * val part = MultipartBody.Part.createFormData("image", file.name, countingBody)
 * ```
 *
 * [onProgress] is called from OkHttp's IO thread. [StateFlow.value] is thread-safe
 * so it can be updated directly. For other UI state, switch to the main thread via
 * `withContext(Dispatchers.Main)` if needed.
 *
 * @param delegate   The original request body whose bytes are counted.
 * @param onProgress Callback receiving (bytesWritten, totalBytes). totalBytes may
 *                   be -1 if the content length is unknown; handle gracefully.
 */
class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = delegate.contentLength()
        var totalBytesWritten = 0L

        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                totalBytesWritten += byteCount
                onProgress(totalBytesWritten, total)
            }
        }

        // Buffer the counting sink so OkHttp's multipart writer can use it correctly.
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
