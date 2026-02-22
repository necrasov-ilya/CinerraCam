package com.cinerracam.core.api

interface CompressionEngine {
    fun compress(frameBytes: ByteArray, metadataBytes: ByteArray): ByteArray

    fun flush(): ByteArray?

    fun close()
}
