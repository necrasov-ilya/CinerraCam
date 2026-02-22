package com.cinerracam.storage.dng

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ClipSessionLayout(
    val rootDir: File,
    val clipDir: File,
    val framesDir: File,
    val manifestFile: File,
    val audioFile: File,
) {
    companion object {
        private val clipFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)

        fun create(baseOutputPath: String): ClipSessionLayout {
            val root = File(baseOutputPath).also { it.mkdirs() }
            val clipName = "clip_${clipFormatter.format(Instant.now())}"
            val clipDir = File(root, clipName).also { it.mkdirs() }
            val framesDir = File(clipDir, "frames").also { it.mkdirs() }

            return ClipSessionLayout(
                rootDir = root,
                clipDir = clipDir,
                framesDir = framesDir,
                manifestFile = File(clipDir, "manifest.json"),
                audioFile = File(clipDir, "audio.wav"),
            )
        }
    }
}
