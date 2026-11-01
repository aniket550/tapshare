package com.aniket.tapshare.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.aniket.tapshare.util.TapLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Moves a file that Nearby Connections received (generic name, no extension) to where the user
 * expects it, with its real name: photos → Pictures/TapShare, videos → Movies/TapShare,
 * everything else → Download/TapShare. No storage permission needed on Android 10+.
 */
object ReceivedFileSaver {

    sealed interface Result {
        data class Saved(val uri: Uri) : Result
        data object NoSpace : Result
        data object Failed : Result
    }

    suspend fun save(context: Context, source: Uri, name: String, mime: String): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (collection, folder) = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/TapShare")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = try {
            resolver.insert(collection, values)
        } catch (e: IllegalArgumentException) {
            TapLog.w("Saver: insert rejected for $mime", e)
            null
        } ?: return@withContext Result.Failed

        try {
            val input = resolver.openInputStream(source) ?: throw IOException("Can't open received file")
            input.use { inp ->
                val out = resolver.openOutputStream(target) ?: throw IOException("Can't open target")
                out.use { inp.copyTo(it, bufferSize = 256 * 1024) }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            // Remove Nearby's temporary copy (may not be allowed on every phone — that's fine).
            runCatching { resolver.delete(source, null, null) }
            Result.Saved(target)
        } catch (e: IOException) {
            runCatching { resolver.delete(target, null, null) }
            val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true
            TapLog.e("Saver: copy failed (noSpace=$noSpace)", e)
            if (noSpace) Result.NoSpace else Result.Failed
        }
    }

    /** Strip path separators and control characters from a name another phone sent us. */
    fun safeName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimStart('.')
        return cleaned.take(120).ifEmpty { "file" }
    }
}
