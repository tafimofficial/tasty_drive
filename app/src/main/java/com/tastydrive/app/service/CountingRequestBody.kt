package com.tastydrive.app.service

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.InputStream

class CountingRequestBody(
    private val inputStreamProvider: () -> InputStream?,
    private val mediaType: MediaType?,
    private val contentLength: Long,
    private val onProgressUpdate: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        val stream = inputStreamProvider() ?: throw IllegalStateException("Could not open input stream")
        stream.use { input ->
            val countingSink = CountingSink(sink)
            val bufferedSink = countingSink.buffer()
            val buffer = ByteArray(64 * 1024) // 64KB buffer
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                bufferedSink.write(buffer, 0, read)
                bufferedSink.flush()
            }
        }
    }

    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var bytesWritten = 0L

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            onProgressUpdate(bytesWritten, contentLength)
        }
    }
}

