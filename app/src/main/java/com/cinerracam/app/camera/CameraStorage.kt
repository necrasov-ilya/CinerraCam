package com.cinerracam.app.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream

data class SaveResult(
    val uri: Uri,
    val bytesWritten: Long,
)

object CameraStorage {
    private const val DNG_MIME = "image/x-adobe-dng"

    fun saveDng(
        context: Context,
        displayName: String,
        relativePath: String,
        writer: (OutputStream) -> Unit,
    ): SaveResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveDngScoped(context, displayName, relativePath, writer)
        } else {
            saveDngLegacy(context, displayName, relativePath, writer)
        }
    }

    private fun saveDngScoped(
        context: Context,
        displayName: String,
        relativePath: String,
        writer: (OutputStream) -> Unit,
    ): SaveResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, DNG_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) {
            "Failed to create MediaStore item"
        }

        val bytesWritten = try {
            resolver.openOutputStream(uri)?.use { stream ->
                val counting = CountingOutputStream(stream)
                writer(counting)
                counting.flush()
                counting.bytesWritten
            } ?: 0L
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }

        val publishValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, publishValues, null, null)

        return SaveResult(uri = uri, bytesWritten = bytesWritten)
    }

    @Suppress("DEPRECATION")
    private fun saveDngLegacy(
        context: Context,
        displayName: String,
        relativePath: String,
        writer: (OutputStream) -> Unit,
    ): SaveResult {
        val cleanRelative = relativePath.removePrefix("${Environment.DIRECTORY_DCIM}/")
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val targetDir = File(dcimDir, cleanRelative).apply { mkdirs() }
        val targetFile = File(targetDir, displayName)

        val bytesWritten = FileOutputStream(targetFile).use { stream ->
            val counting = CountingOutputStream(stream)
            writer(counting)
            counting.flush()
            counting.bytesWritten
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf(DNG_MIME),
            null,
        )

        return SaveResult(uri = Uri.fromFile(targetFile), bytesWritten = bytesWritten)
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten += 1
        }

        override fun write(b: ByteArray) {
            out.write(b)
            bytesWritten += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len
        }
    }
}
