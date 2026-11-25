package com.nova.converter.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** SAF file helpers - input pick + cache copy + duration probe */
object Files {

    fun copyToCache(context: Context, uri: Uri, suffix: String = ""): File? {
        return try {
            val name = queryName(context, uri) ?: "input"
            val dir = File(context.cacheDir, "nova_in").apply { mkdirs() }
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val out = File(dir, if (safe.contains('.')) safe else safe + suffix)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return null
            out
        } catch (e: Exception) {
            null
        }
    }

    fun queryName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) return c.getString(i)
                }
            }
        return uri.lastPathSegment
    }

    fun querySize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.SIZE)
                    if (i >= 0) return c.getLong(i)
                }
            }
        return 0L
    }

    /** MediaMetadataRetriever se duration ms (0 agar na mile) */
    fun durationMs(path: String): Long {
        return try {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(path)
            (r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()) ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun baseName(name: String): String = name.substringBeforeLast('.')
}
