package com.cinerracam.storage.dng

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import com.cinerracam.core.api.CompressionEngine
import com.cinerracam.core.api.FrameSink
import com.cinerracam.core.model.FramePacket
import com.cinerracam.core.model.RecorderConfig
import java.io.File

class DngFrameSink(
    private val cameraCharacteristics: CameraCharacteristics,
    private val manifestWriter: ManifestWriter = ManifestWriter(),
    private val compressionEngine: CompressionEngine? = null,
) : FrameSink {
    private var config: RecorderConfig? = null
    private var layout: ClipSessionLayout? = null
    private var startedAtEpochMs: Long = 0L

    private var framesCaptured: Long = 0L
    private var framesWritten: Long = 0L
    private var framesDropped: Long = 0L
    private var totalWriteMs: Double = 0.0

    override suspend fun openSession(config: RecorderConfig) {
        this.config = config
        this.layout = ClipSessionLayout.create(config.outputUri)
        this.startedAtEpochMs = System.currentTimeMillis()

        this.framesCaptured = 0
        this.framesWritten = 0
        this.framesDropped = 0
        this.totalWriteMs = 0.0

        manifestWriter.open(requireNotNull(layout).manifestFile)
    }

    override suspend fun write(packet: FramePacket) {
        val localConfig = requireNotNull(config) { "Session is not opened" }
        val localLayout = requireNotNull(layout) { "Layout is not initialized" }
        val image = packet.imageRef as? Image
            ?: throw IllegalArgumentException("FramePacket.imageRef must be android.media.Image")
        val captureResult = packet.captureMetadataRef as? CaptureResult
            ?: throw IllegalArgumentException("FramePacket.captureMetadataRef must be CaptureResult")

        framesCaptured += 1

        val startedNs = System.nanoTime()
        try {
            val dngFile = File(localLayout.framesDir, "frame_${packet.frameIndex.toString().padStart(6, '0')}.dng")
            dngFile.outputStream().buffered().use { output ->
                DngCreator(cameraCharacteristics, captureResult).use { creator ->
                    creator.writeImage(output, image)
                }
            }

            framesWritten += 1
            totalWriteMs += (System.nanoTime() - startedNs) / 1_000_000.0

            if (compressionEngine != null) {
                val headerBytes = buildString {
                    append(localConfig.cameraId)
                    append('|')
                    append(packet.frameIndex)
                    append('|')
                    append(packet.sensorTimestampNs)
                }.encodeToByteArray()
                compressionEngine.compress(byteArrayOf(), headerBytes)
            }
        } finally {
            image.close()
        }
    }

    override suspend fun recordDrop(frameIndex: Long, sensorTimestampNs: Long, reason: String) {
        framesCaptured += 1
        framesDropped += 1
        manifestWriter.recordDrop(frameIndex, sensorTimestampNs, reason)
    }

    override suspend fun closeSession() {
        val localConfig = config ?: return
        val finishedAtEpochMs = System.currentTimeMillis()

        val avgWriteMs = if (framesWritten > 0) totalWriteMs / framesWritten else 0.0

        val manifest = SessionManifest(
            formatVersion = 1,
            cameraId = localConfig.cameraId,
            resolution = localConfig.resolution.label,
            targetFps = localConfig.targetFps,
            audioEnabled = localConfig.audioEnabled,
            compressionMode = localConfig.compressionMode.name,
            startedAtEpochMs = startedAtEpochMs,
            finishedAtEpochMs = finishedAtEpochMs,
            metrics = ManifestMetrics(
                framesCaptured = framesCaptured,
                framesWritten = framesWritten,
                framesDropped = framesDropped,
                avgWriteMs = avgWriteMs,
            ),
            drops = emptyList(),
        )
        manifestWriter.write(manifest)

        compressionEngine?.flush()
        compressionEngine?.close()

        config = null
        layout = null
    }
}
