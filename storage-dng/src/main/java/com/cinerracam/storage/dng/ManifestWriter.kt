package com.cinerracam.storage.dng

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ManifestWriter {
    private val drops = mutableListOf<ManifestDropEntry>()
    private val json = Json {
        prettyPrint = true
    }

    private var outputFile: File? = null

    fun open(file: File) {
        outputFile = file
        drops.clear()
    }

    fun recordDrop(frameIndex: Long, sensorTimestampNs: Long, reason: String) {
        synchronized(drops) {
            drops += ManifestDropEntry(frameIndex, sensorTimestampNs, reason)
        }
    }

    fun write(manifest: SessionManifest) {
        val target = outputFile ?: return
        val manifestWithDrops = manifest.copy(
            drops = synchronized(drops) { drops.toList() },
        )
        target.writeText(json.encodeToString(manifestWithDrops))
    }
}
