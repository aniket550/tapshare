package com.aniket.tapshare.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import com.aniket.tapshare.handshake.ItemInfo
import com.aniket.tapshare.util.TapLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** A photo/file the user wants to send (from the share sheet or our pickers). */
data class OutgoingItem(val uri: Uri, val name: String, val mime: String, val size: Long) {
    val info: ItemInfo get() = ItemInfo(name, mime, size)
}

/**
 * Items waiting to be sent. While it isn't empty, a tap starts an AirDrop-style SEND session
 * instead of a contact exchange. Cleared after a successful send or when the user cancels.
 */
class Outbox(private val context: Context) {

    private val _items = MutableStateFlow<List<OutgoingItem>>(emptyList())
    val items: StateFlow<List<OutgoingItem>> = _items.asStateFlow()

    /** Reads name/size/type for each Uri (off the main thread). Unreadable Uris are skipped. */
    suspend fun set(uris: List<Uri>) {
        val resolved = withContext(Dispatchers.IO) { uris.distinct().mapNotNull(::describe) }
        _items.value = resolved
        TapLog.i("Outbox: ${resolved.size} item(s) ready")
    }

    fun clear() {
        _items.value = emptyList()
    }

    /** Small preview of the first photo/video for the receiver's "wants to share" sheet. */
    suspend fun previewJpeg(maxBytes: Int = 9 * 1024): ByteArray? = withContext(Dispatchers.IO) {
        val first = _items.value.firstOrNull { it.mime.startsWith("image/") || it.mime.startsWith("video/") }
            ?: return@withContext null
        val thumb = loadThumbnail(context, first.uri, 160) ?: return@withContext null
        var quality = 80
        while (quality >= 30) {
            val bytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            if (bytes.size <= maxBytes) return@withContext bytes
            quality -= 10
        }
        null
    }

    private fun describe(uri: Uri): OutgoingItem? {
        val resolver = context.contentResolver
        return try {
            var name: String? = null
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
            OutgoingItem(
                uri = uri,
                name = name ?: uri.lastPathSegment ?: "file",
                mime = resolver.getType(uri) ?: "application/octet-stream",
                size = size,
            )
        } catch (e: SecurityException) {
            TapLog.w("Outbox: no access to $uri", e)
            null
        } catch (e: IllegalArgumentException) {
            TapLog.w("Outbox: bad uri $uri", e)
            null
        }
    }
}

/** System thumbnail for a content Uri (works for photos and videos), or null. Call off the main thread. */
fun loadThumbnail(context: Context, uri: Uri, sizePx: Int): Bitmap? = try {
    context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
} catch (e: Exception) {
    // IOException / SecurityException / UnsupportedOperationException for files without a preview.
    null
}
