package com.cinerracam.app.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinerracam.core.api.RecorderController
import com.cinerracam.core.model.CompressionMode
import com.cinerracam.core.model.RecordMetrics
import com.cinerracam.core.model.RecorderConfig
import com.cinerracam.core.model.RecorderState
import com.cinerracam.core.model.Resolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class RecorderUiState(
    val recorderState: RecorderState = RecorderState.Idle,
    val metrics: RecordMetrics = RecordMetrics.empty(),
    val audioEnabled: Boolean = true,
    val fakeDataMode: Boolean = true,
    val targetResolution: Resolution = Resolution(3840, 2160),
    val targetFps: Int = 30,
    val sensorStatus: String = "RAW ready (fake pipeline)",
)

class RecorderViewModel(
    private val controller: RecorderController = FakeRecorderController(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = mutableUiState.asStateFlow()

    private var latestConfig: RecorderConfig = defaultConfig(audioEnabled = true)

    init {
        observeController()
        viewModelScope.launch {
            controller.prepare(latestConfig)
        }
    }

    fun onRecordClick() {
        viewModelScope.launch {
            when (uiState.value.recorderState) {
                is RecorderState.Recording -> controller.stop()
                is RecorderState.Idle -> controller.start()
                else -> Unit
            }
        }
    }

    fun onAudioEnabledChange(enabled: Boolean) {
        mutableUiState.update { it.copy(audioEnabled = enabled) }
        latestConfig = defaultConfig(audioEnabled = enabled)

        if (uiState.value.recorderState is RecorderState.Idle) {
            viewModelScope.launch { controller.prepare(latestConfig) }
        }
    }

    fun onFakeDataModeChange(enabled: Boolean) {
        mutableUiState.update {
            it.copy(
                fakeDataMode = enabled,
                sensorStatus = if (enabled) {
                    "RAW ready (fake pipeline)"
                } else {
                    "RAW probe pending (camera pipeline)"
                },
            )
        }
    }

    private fun observeController() {
        viewModelScope.launch {
            controller.currentState().collect { state ->
                mutableUiState.update { it.copy(recorderState = state) }
            }
        }

        viewModelScope.launch {
            controller.metricsFlow().collect { metrics ->
                mutableUiState.update { it.copy(metrics = metrics) }
            }
        }
    }

    private fun defaultConfig(audioEnabled: Boolean): RecorderConfig {
        return RecorderConfig(
            cameraId = "0",
            resolution = uiState.value.targetResolution,
            targetFps = uiState.value.targetFps,
            audioEnabled = audioEnabled,
            outputUri = "recordings",
            compressionMode = CompressionMode.NATIVE_LZ4,
        )
    }

    override fun onCleared() {
        if (controller is FakeRecorderController) {
            controller.dispose()
        }
        super.onCleared()
    }
}

private class FakeRecorderController : RecorderController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    private val metrics = MutableStateFlow(RecordMetrics.empty())

    private var config: RecorderConfig? = null
    private var tickerJob: Job? = null

    override suspend fun prepare(config: RecorderConfig) {
        this.config = config
        state.value = RecorderState.Preparing(config)
        delay(180)
        state.value = RecorderState.Idle
    }

    override suspend fun start() {
        val currentConfig = config ?: return
        if (state.value is RecorderState.Recording) {
            return
        }

        state.value = RecorderState.Recording(
            config = currentConfig,
            startedAtNs = SystemClock.elapsedRealtimeNanos(),
        )

        tickerJob?.cancel()
        tickerJob = scope.launch {
            var frame = 0L
            var totalWriteMs = 0.0
            var highWatermark = 0

            while (isActive) {
                delay(42)
                frame += 1

                val dropped = if (frame % 21L == 0L) 1L else 0L
                val writeMs = 6.0 + Random.nextDouble(0.0, 3.5)
                totalWriteMs += writeMs

                val queueDepth = (2..11).random()
                highWatermark = maxOf(highWatermark, queueDepth)

                val prev = metrics.value
                val captured = prev.framesCaptured + 1
                val written = prev.framesWritten + (1 - dropped)
                val drops = prev.framesDropped + dropped
                val avg = if (written > 0) totalWriteMs / written else 0.0

                metrics.value = RecordMetrics(
                    framesCaptured = captured,
                    framesWritten = written,
                    framesDropped = drops,
                    avgWriteMs = avg,
                    queueHighWatermark = highWatermark,
                )
            }
        }
    }

    override suspend fun stop() {
        val currentConfig = config ?: return
        if (state.value !is RecorderState.Recording) {
            return
        }

        state.value = RecorderState.Stopping(currentConfig)
        tickerJob?.cancel()
        tickerJob = null
        delay(120)
        state.value = RecorderState.Idle
    }

    override fun currentState(): StateFlow<RecorderState> = state.asStateFlow()

    override fun metricsFlow(): StateFlow<RecordMetrics> = metrics.asStateFlow()

    fun dispose() {
        scope.cancel()
    }
}
