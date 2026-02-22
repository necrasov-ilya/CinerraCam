package com.cinerracam.core.api

import com.cinerracam.core.model.RecordMetrics
import com.cinerracam.core.model.RecorderConfig
import com.cinerracam.core.model.RecorderState
import kotlinx.coroutines.flow.StateFlow

interface RecorderController {
    suspend fun prepare(config: RecorderConfig)

    suspend fun start()

    suspend fun stop()

    fun currentState(): StateFlow<RecorderState>

    fun metricsFlow(): StateFlow<RecordMetrics>
}
