package com.cinerracam.nativewriter

import com.cinerracam.core.api.CompressionEngine
import com.cinerracam.core.model.CompressionMode

class NativeCompressionEngine(
    private val mode: CompressionMode,
) : CompressionEngine {

    override fun compress(frameBytes: ByteArray, metadataBytes: ByteArray): ByteArray {
        if (!nativeLoaded) {
            return metadataBytes + frameBytes
        }
        return nativeCompress(frameBytes, metadataBytes, mode.ordinal)
    }

    override fun flush(): ByteArray? {
        if (!nativeLoaded) {
            return null
        }
        return nativeFlush()
    }

    override fun close() {
        if (nativeLoaded) {
            nativeClose()
        }
    }

    private external fun nativeCompress(frameBytes: ByteArray, metadataBytes: ByteArray, mode: Int): ByteArray

    private external fun nativeFlush(): ByteArray?

    private external fun nativeClose()

    companion object {
        private val nativeLoaded: Boolean = runCatching {
            System.loadLibrary("native_writer")
            true
        }.getOrDefault(false)
    }
}
