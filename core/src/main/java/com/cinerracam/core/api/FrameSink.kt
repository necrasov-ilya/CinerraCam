package com.cinerracam.core.api

import com.cinerracam.core.model.FramePacket
import com.cinerracam.core.model.RecorderConfig

interface FrameSink {
    suspend fun openSession(config: RecorderConfig)

    suspend fun write(packet: FramePacket)

    suspend fun recordDrop(frameIndex: Long, sensorTimestampNs: Long, reason: String)

    suspend fun closeSession()
}
