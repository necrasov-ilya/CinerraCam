package com.cinerracam.benchmark

import java.io.File
import kotlin.math.max
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: benchmark <manifest-file-or-directory> [more paths]")
        return
    }

    val manifests = args
        .flatMap { collectManifestFiles(File(it)) }
        .mapNotNull(::readManifest)

    if (manifests.isEmpty()) {
        println("No valid manifest files found")
        return
    }

    val totalCaptured = manifests.sumOf { it.metrics.framesCaptured }
    val totalWritten = manifests.sumOf { it.metrics.framesWritten }
    val totalDropped = manifests.sumOf { it.metrics.framesDropped }

    val avgWriteMs = manifests.map { it.metrics.avgWriteMs }.average()

    val maxDurationMs = manifests.fold(0L) { acc, item ->
        max(acc, item.finishedAtEpochMs - item.startedAtEpochMs)
    }

    val dropRate = if (totalCaptured > 0) {
        (totalDropped.toDouble() / totalCaptured.toDouble()) * 100.0
    } else {
        0.0
    }

    println("Benchmark summary")
    println("- sessions: ${manifests.size}")
    println("- total captured: $totalCaptured")
    println("- total written: $totalWritten")
    println("- total dropped: $totalDropped")
    println("- drop rate: ${"%.2f".format(dropRate)}%")
    println("- average write: ${"%.2f".format(avgWriteMs)} ms")
    println("- max session duration: $maxDurationMs ms")

    manifests.forEachIndexed { index, item ->
        println("run #${index + 1}: camera=${item.cameraId} res=${item.resolution} fps=${item.targetFps}")
    }
}

private fun collectManifestFiles(path: File): List<File> {
    if (!path.exists()) {
        return emptyList()
    }

    if (path.isFile) {
        return if (path.name.equals("manifest.json", ignoreCase = true)) listOf(path) else emptyList()
    }

    return path.walkTopDown()
        .filter { it.isFile && it.name.equals("manifest.json", ignoreCase = true) }
        .toList()
}

private fun readManifest(file: File): SessionManifest? {
    val json = Json { ignoreUnknownKeys = true }
    return runCatching {
        json.decodeFromString<SessionManifest>(file.readText())
    }.getOrNull()
}
