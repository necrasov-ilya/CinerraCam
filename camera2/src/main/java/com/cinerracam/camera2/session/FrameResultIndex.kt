package com.cinerracam.camera2.session

import android.hardware.camera2.TotalCaptureResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

internal class FrameResultIndex(
    private val maxEntries: Int = 256,
) {
    private val byTimestamp = ConcurrentHashMap<Long, TotalCaptureResult>()
    private val order = ConcurrentLinkedQueue<Long>()

    fun put(result: TotalCaptureResult, timestampNs: Long) {
        byTimestamp[timestampNs] = result
        order.add(timestampNs)
        trimIfNeeded()
    }

    fun take(timestampNs: Long): TotalCaptureResult? {
        order.remove(timestampNs)
        return byTimestamp.remove(timestampNs)
    }

    private fun trimIfNeeded() {
        while (order.size > maxEntries) {
            val oldest = order.poll() ?: break
            byTimestamp.remove(oldest)
        }
    }
}
