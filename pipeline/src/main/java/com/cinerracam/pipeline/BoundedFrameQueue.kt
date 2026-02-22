package com.cinerracam.pipeline

import com.cinerracam.core.model.FramePacket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface QueueOfferResult {
    data object Enqueued : QueueOfferResult

    data class Dropped(
        val droppedPacket: FramePacket?,
        val reason: String,
    ) : QueueOfferResult
}

class BoundedFrameQueue(
    private val capacity: Int,
    private val backpressurePolicy: BackpressurePolicy,
) {
    private val buffer = ArrayDeque<FramePacket>()
    private val mutex = Mutex()

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    suspend fun offer(packet: FramePacket): QueueOfferResult = mutex.withLock {
        if (buffer.size < capacity) {
            buffer.addLast(packet)
            return@withLock QueueOfferResult.Enqueued
        }

        return@withLock when (backpressurePolicy) {
            BackpressurePolicy.DROP_NEWEST -> QueueOfferResult.Dropped(
                droppedPacket = packet,
                reason = "queue_full_drop_newest",
            )

            BackpressurePolicy.DROP_OLDEST -> {
                val removed = buffer.removeFirstOrNull()
                buffer.addLast(packet)
                QueueOfferResult.Dropped(
                    droppedPacket = removed,
                    reason = "queue_full_drop_oldest",
                )
            }
        }
    }

    suspend fun poll(): FramePacket? = mutex.withLock {
        buffer.removeFirstOrNull()
    }

    suspend fun size(): Int = mutex.withLock { buffer.size }
}
