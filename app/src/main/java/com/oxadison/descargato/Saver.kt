// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

object Saver {
    private val TEMP_SUFFIXES = listOf(".part", ".ytdl", ".temp", ".tmp")
    private val SIDE_EXTENSIONS = setOf("vtt", "srt", "ttml", "json", "ass")

    fun isFinalFile(f: File): Boolean {
        if (!f.isFile) return false
        val name = f.name.lowercase()
        if (TEMP_SUFFIXES.any { name.endsWith(it) } || ".part-" in name) return false
        return f.extension.lowercase() !in SIDE_EXTENSIONS
    }

    fun saveToDownloads(context: Context, src: File): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, src.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeFor(src.extension.lowercase()))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DescarGato")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("No se pudo crear el archivo en Descargas.")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("No se pudo abrir el archivo de destino.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return src.name
    }

    private fun mimeFor(ext: String): String = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
