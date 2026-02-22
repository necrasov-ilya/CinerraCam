package com.cinerracam.core.model

data class DropRecord(
    val frameIndex: Long,
    val sensorTimestampNs: Long,
    val reason: String,
)

data class RecordMetrics(
    val framesCaptured: Long,
    val framesWritten: Long,
    val framesDropped: Long,
    val avgWriteMs: Double,
    val queueHighWatermark: Int,
) {
    companion object {
        fun empty(): RecordMetrics = RecordMetrics(
            framesCaptured = 0,
            framesWritten = 0,
            framesDropped = 0,
            avgWriteMs = 0.0,
            queueHighWatermark = 0,
        )
    }
}
