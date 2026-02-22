package com.cinerracam.app.camera.internal

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import com.cinerracam.app.camera.RecordingStats
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

internal interface RawFramePipelineListener {
    fun onStatus(message: String)

    fun onStats(stats: RecordingStats)

    fun onLastSaved(uri: String)

    fun onError(message: String, throwable: Throwable? = null)
}

internal class RawFramePipeline(
    private val context: Context,
    private val listener: RawFramePipelineListener,
    private val getCameraCharacteristics: () -> CameraCharacteristics?,
    private val isRecordingProvider: () -> Boolean,
    private val recordingRelativePathProvider: () -> String,
    private val elapsedSecProvider: () -> Int,
    private val timestampLabelProvider: () -> String,
) {
    private data class PendingImage(
        val image: Image,
        val kind: PendingKind,
        val frameIndex: Long,
    )

    private enum class PendingKind {
        PHOTO,
        RECORD,
    }

    private companion object {
        private const val MAX_RESULT_CACHE = 256
        private const val WRITE_QUEUE_CAPACITY = 3
        private const val PHOTO_RELATIVE_PATH = "DCIM/CinerraCam/Photos"
    }

    private val resultByTimestamp = HashMap<Long, TotalCaptureResult>()
    private val resultOrder = ArrayDeque<Long>()
    private val imageByTimestamp = HashMap<Long, PendingImage>()
    private val imageOrder = ArrayDeque<Long>()

    private val writerExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(WRITE_QUEUE_CAPACITY),
        ThreadFactory { runnable ->
            Thread(runnable, "cinerracam-dng-writer").apply { isDaemon = true }
        },
    )

    private val frameCounter = AtomicLong(0)
    private val pendingPhotoCaptures = AtomicInteger(0)

    private val statsLock = Any()
    private var stats: RecordingStats = RecordingStats()
    private var totalWriteMs: Double = 0.0
    private var dropStatusCounter: Long = 0L

    init {
        writerExecutor.rejectedExecutionHandler = ThreadPoolExecutor.AbortPolicy()
    }

    fun markPhotoCaptureRequested() {
        pendingPhotoCaptures.incrementAndGet()
    }

    fun rollbackPhotoCaptureRequest() {
        while (true) {
            val value = pendingPhotoCaptures.get()
            if (value <= 0) {
                return
            }
            if (pendingPhotoCaptures.compareAndSet(value, value - 1)) {
                return
            }
        }
    }

    fun resetRecordingStats() {
        frameCounter.set(0L)
        synchronized(statsLock) {
            stats = RecordingStats()
            totalWriteMs = 0.0
            dropStatusCounter = 0L
        }
        emitStats()
    }

    fun emitCurrentStats() {
        emitStats()
    }

    fun reportDrop(reason: String) {
        onFrameDropped(reason)
    }

    fun onCaptureResult(result: TotalCaptureResult) {
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        resultByTimestamp[timestamp] = result
        resultOrder.add(timestamp)
        processPendingPair(timestamp)
        trimPendingBuffers()
    }

    fun onRawImage(image: Image) {
        val timestampNs = image.timestamp
        val isRecording = isRecordingProvider()
        val isPhotoRequest = pendingPhotoCaptures.get() > 0 && !isRecording

        if (!isPhotoRequest && !isRecording) {
            image.close()
            return
        }

        val pendingImage = if (isPhotoRequest) {
            pendingPhotoCaptures.decrementAndGet()
            PendingImage(image = image, kind = PendingKind.PHOTO, frameIndex = 0L)
        } else {
            val index = frameCounter.incrementAndGet()
            onFrameCaptured()
            PendingImage(image = image, kind = PendingKind.RECORD, frameIndex = index)
        }

        imageByTimestamp[timestampNs] = pendingImage
        imageOrder.add(timestampNs)
        processPendingPair(timestampNs)
        trimPendingBuffers()
    }

    fun clearPendingBuffers() {
        imageByTimestamp.values.forEach { pending ->
            runCatching { pending.image.close() }
        }
        imageByTimestamp.clear()
        imageOrder.clear()
        resultByTimestamp.clear()
        resultOrder.clear()
    }

    fun close() {
        clearPendingBuffers()
        writerExecutor.shutdownNow()
    }

    private fun submitWriteTask(task: FrameWriteTask, dropReason: String): Boolean {
        return try {
            writerExecutor.execute(task)
            updateQueueHighWatermark(writerExecutor.queue.size)
            true
        } catch (_: RejectedExecutionException) {
            onFrameDropped(dropReason)
            false
        }
    }

    private fun processPendingPair(timestampNs: Long) {
        val pendingImage = imageByTimestamp[timestampNs] ?: return
        val captureResult = resultByTimestamp[timestampNs] ?: return

        imageByTimestamp.remove(timestampNs)
        resultByTimestamp.remove(timestampNs)
        imageOrder.remove(timestampNs)
        resultOrder.remove(timestampNs)

        val task = FrameWriteTask(
            context = context,
            image = pendingImage.image,
            result = captureResult,
            fileName = if (pendingImage.kind == PendingKind.PHOTO) {
                "IMG_${timestampLabelProvider()}.dng"
            } else {
                "frame_${pendingImage.frameIndex.toString().padStart(6, '0')}.dng"
            },
            relativePath = if (pendingImage.kind == PendingKind.PHOTO) {
                PHOTO_RELATIVE_PATH
            } else {
                recordingRelativePathProvider()
            },
            updateRecordingStats = pendingImage.kind == PendingKind.RECORD,
            frameIndex = pendingImage.frameIndex,
            getCameraCharacteristics = getCameraCharacteristics,
            onFrameWritten = ::onFrameWritten,
            onFrameDropped = ::onFrameDropped,
            onPhotoSaved = { listener.onStatus("Фото сохранено") },
            onLastSaved = listener::onLastSaved,
            onError = listener::onError,
        )

        val dropReason = if (pendingImage.kind == PendingKind.PHOTO) {
            "photo_write_queue_overflow"
        } else {
            "record_write_queue_overflow"
        }

        if (!submitWriteTask(task, dropReason)) {
            pendingImage.image.close()
        }
    }

    private fun trimPendingBuffers() {
        while (resultOrder.size > MAX_RESULT_CACHE) {
            val oldest = resultOrder.removeFirstOrNull() ?: break
            resultByTimestamp.remove(oldest)
        }

        while (imageOrder.size > MAX_RESULT_CACHE) {
            val oldest = imageOrder.removeFirstOrNull() ?: break
            val stale = imageByTimestamp.remove(oldest) ?: continue
            stale.image.close()
            if (stale.kind == PendingKind.RECORD) {
                onFrameDropped("stale_image_without_result")
            }
        }
    }

    private fun onFrameCaptured() {
        synchronized(statsLock) {
            stats = stats.copy(
                capturedFrames = stats.capturedFrames + 1,
                elapsedSec = elapsedSecProvider(),
            )
        }
        emitStats()
    }

    private fun onFrameWritten(writeMs: Double, bytesWritten: Long, frameIndex: Long) {
        synchronized(statsLock) {
            totalWriteMs += writeMs
            val written = stats.writtenFrames + 1
            val writtenMb = stats.writtenMb + (bytesWritten.toDouble() / (1024.0 * 1024.0))

            stats = stats.copy(
                writtenFrames = written,
                avgWriteMs = if (written > 0) totalWriteMs / written else 0.0,
                writtenMb = writtenMb,
                elapsedSec = elapsedSecProvider(),
            )
        }

        if (frameIndex % 10L == 0L || frameIndex <= 3L) {
            emitStats()
        }
    }

    private fun onFrameDropped(reason: String) {
        synchronized(statsLock) {
            stats = stats.copy(
                droppedFrames = stats.droppedFrames + 1,
                elapsedSec = elapsedSecProvider(),
            )
        }
        dropStatusCounter += 1
        if (dropStatusCounter % 15L == 1L) {
            listener.onStatus("Дропы кадров: $reason")
        }
        emitStats()
    }

    private fun updateQueueHighWatermark(queueSize: Int) {
        synchronized(statsLock) {
            stats = stats.copy(
                queueHighWatermark = max(stats.queueHighWatermark, queueSize),
                elapsedSec = elapsedSecProvider(),
            )
        }
        emitStats()
    }

    private fun emitStats() {
        val snapshot = synchronized(statsLock) { stats }
        listener.onStats(snapshot)
    }
}
