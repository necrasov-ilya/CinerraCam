package com.cinerracam.pipeline

import com.cinerracam.core.api.FrameSink
import com.cinerracam.core.model.FramePacket
import com.cinerracam.core.model.RecordMetrics
import com.cinerracam.core.model.RecorderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.measureNanoTime

class RecorderPipeline(
    private val frameSink: FrameSink,
    queueCapacity: Int = 12,
    backpressurePolicy: BackpressurePolicy = BackpressurePolicy.DROP_NEWEST,
) {
    private val queue = BoundedFrameQueue(queueCapacity, backpressurePolicy)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableMetrics = MutableStateFlow(RecordMetrics.empty())

    private var consumerJob: Job? = null
    private var isOpened: Boolean = false

    fun metricsFlow(): StateFlow<RecordMetrics> = mutableMetrics.asStateFlow()

    suspend fun open(config: RecorderConfig) {
        if (isOpened) {
            return
        }

        frameSink.openSession(config)
        isOpened = true

        consumerJob = scope.launch {
            consumeLoop()
        }
    }

    suspend fun submit(packet: FramePacket) {
        if (!isOpened) {
            return
        }

        val queueResult = queue.offer(packet)
        val queueSize = queue.size()

        when (queueResult) {
            QueueOfferResult.Enqueued -> {
                mutableMetrics.update {
                    it.copy(
                        framesCaptured = it.framesCaptured + 1,
                        queueHighWatermark = maxOf(it.queueHighWatermark, queueSize),
                    )
                }
            }

            is QueueOfferResult.Dropped -> {
                val dropped = queueResult.droppedPacket ?: packet
                frameSink.recordDrop(
                    frameIndex = dropped.frameIndex,
                    sensorTimestampNs = dropped.sensorTimestampNs,
                    reason = queueResult.reason,
                )
                mutableMetrics.update {
                    it.copy(
                        framesCaptured = it.framesCaptured + 1,
                        framesDropped = it.framesDropped + 1,
                        queueHighWatermark = maxOf(it.queueHighWatermark, queueSize),
                    )
                }
            }
        }
    }

    suspend fun close() {
        val job = consumerJob
        if (job != null) {
            job.cancelAndJoin()
        }
        consumerJob = null

        if (isOpened) {
            frameSink.closeSession()
            isOpened = false
        }
    }

    private suspend fun consumeLoop() {
        var totalWriteNs = 0.0

        while (scope.isActive) {
            val packet = queue.poll()
            if (packet == null) {
                delay(2)
                continue
            }

            val writeNs = measureNanoTime {
                runCatching {
                    frameSink.write(packet)
                }.onFailure {
                    frameSink.recordDrop(
                        frameIndex = packet.frameIndex,
                        sensorTimestampNs = packet.sensorTimestampNs,
                        reason = "write_failed:${it.javaClass.simpleName}",
                    )
                }
            }

            totalWriteNs += writeNs.toDouble()
            mutableMetrics.update {
                val written = it.framesWritten + 1
                val avgWriteMs = if (written > 0) (totalWriteNs / written) / 1_000_000.0 else 0.0
                it.copy(
                    framesWritten = written,
                    avgWriteMs = avgWriteMs,
                )
            }
        }
    }
}
