package com.cinerracam.core.model

sealed interface RecorderState {
    data object Idle : RecorderState

    data class Preparing(
        val config: RecorderConfig,
    ) : RecorderState

    data class Recording(
        val config: RecorderConfig,
        val startedAtNs: Long,
    ) : RecorderState

    data class Stopping(
        val config: RecorderConfig,
    ) : RecorderState

    data class Error(
        val message: String,
        val recoverable: Boolean,
        val throwable: Throwable? = null,
    ) : RecorderState
}
