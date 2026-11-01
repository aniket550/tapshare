package com.aniket.tapshare.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.aniket.tapshare.util.TapLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * "Send smaller photos": long side ≤ 2048 px, JPEG quality 82 — usually 5–10× smaller, so the
 * transfer is that much faster. Videos and other files are sent as they are. If a photo would not
 * get smaller (already small), the original is sent.
 */
object PhotoShrinker {
    private const val MAX_SIDE = 2048
    private const val QUALITY = 82
    private val SHRINKABLE = setOf("image/jpeg", "image/png", "image/heic", "image/heif", "image/webp")

    suspend fun prepare(context: Context, items: List<OutgoingItem>, shrink: Boolean): List<OutgoingItem> {
        if (!shrink) return items
        return withContext(Dispatchers.IO) {
            val dir = workDir(context).apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            items.mapIndexed { i, item ->
                if (item.mime in SHRINKABLE) shrinkOne(context, item, File(dir, "$i.jpg")) ?: item else item
            }
        }
    }

    fun cleanup(context: Context) {
        workDir(context).listFiles()?.forEach { it.delete() }
    }

    private fun workDir(context: Context) = File(context.cacheDir, "outgoing")

    private fun shrinkOne(context: Context, item: OutgoingItem, out: File): OutgoingItem? = try {
        val source = ImageDecoder.createSource(context.contentResolver, item.uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = MAX_SIDE.toFloat() / max(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // JPEG has no transparency: put see-through PNGs on white instead of black.
        val bitmap = if (decoded.hasAlpha()) {
            Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(decoded, 0f, 0f, null)
                }
            }
        } else {
            decoded
        }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (item.size in 1..out.length()) {
            out.delete()
            null // original is already smaller
        } else {
            OutgoingItem(Uri.fromFile(out), item.name.substringBeforeLast('.') + ".jpg", "image/jpeg", out.length())
        }
    } catch (e: Exception) {
        // Unsupported/corrupt image or no space in cache: just send the original.
        TapLog.w("Shrink failed for ${item.name}, sending original", e)
        null
    }
}
