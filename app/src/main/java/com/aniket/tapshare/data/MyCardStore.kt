package com.aniket.tapshare.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.edit
import com.aniket.tapshare.handshake.ContactCard
import com.aniket.tapshare.util.TapLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** The user's own contact card ("poster"). The user decides which fields are shared. */
data class MyCard(
    val name: String,
    val phone: String,
    val email: String,
    val sharePhone: Boolean,
    val shareEmail: Boolean,
    /** Changes whenever the photo changes, so the UI reloads it. 0 = no photo. */
    val photoVersion: Long,
) {
    val hasName: Boolean get() = name.isNotBlank()
    val hasPhoto: Boolean get() = photoVersion != 0L

    /** "Aniket Raj" → "AR" for the placeholder avatar. */
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

class MyCardStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("tapshare_my_card", Context.MODE_PRIVATE)
    private val photoFile = File(context.filesDir, "my_card_photo.jpg")

    private val _card = MutableStateFlow(load())
    val card: StateFlow<MyCard> = _card.asStateFlow()

    fun update(transform: (MyCard) -> MyCard) {
        val next = transform(_card.value)
        prefs.edit {
            putString(K_NAME, next.name)
            putString(K_PHONE, next.phone)
            putString(K_EMAIL, next.email)
            putBoolean(K_SHARE_PHONE, next.sharePhone)
            putBoolean(K_SHARE_EMAIL, next.shareEmail)
        }
        _card.value = next
    }

    /** Copies the picked photo as a 512×512 centre-cropped JPEG. Returns false if it can't be read. */
    suspend fun setPhoto(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = PHOTO_PX.toFloat() / min(w, h)
                if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val square = centerCrop(bitmap, PHOTO_PX)
            photoFile.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            val version = System.currentTimeMillis()
            prefs.edit { putLong(K_PHOTO_VERSION, version) }
            _card.value = _card.value.copy(photoVersion = version)
            true
        } catch (e: IOException) {
            TapLog.w("MyCard: photo failed", e)
            false
        } catch (e: RuntimeException) {
            // Corrupt / unsupported image (ImageDecoder throws DecodeException or IllegalArgumentException).
            TapLog.w("MyCard: photo failed", e)
            false
        }
    }

    fun removePhoto() {
        photoFile.delete()
        prefs.edit { putLong(K_PHOTO_VERSION, 0L) }
        _card.value = _card.value.copy(photoVersion = 0L)
    }

    /** Full-size photo for our own poster. */
    suspend fun photoBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        if (!_card.value.hasPhoto || !photoFile.exists()) null else BitmapFactory.decodeFile(photoFile.path)
    }

    /**
     * Small JPEG of our photo that fits in a Nearby BYTES message. Quality steps down until it
     * fits [maxBytes]; null if there is no photo.
     */
    suspend fun photoJpeg(sizePx: Int, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        val full = photoBitmap() ?: return@withContext null
        val small = Bitmap.createScaledBitmap(full, sizePx, sizePx, true)
        var quality = 85
        while (quality >= 35) {
            val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            if (bytes.size <= maxBytes) return@withContext bytes
            quality -= 10
        }
        null
    }

    /** What "Share" sends: only the fields the user switched on. */
    suspend fun toContactCard(): ContactCard {
        val c = _card.value
        return ContactCard(
            name = c.name.trim(),
            phone = c.phone.trim().takeIf { c.sharePhone && it.isNotEmpty() },
            email = c.email.trim().takeIf { c.shareEmail && it.isNotEmpty() },
            photoJpeg = photoJpeg(CARD_PHOTO_PX, CARD_PHOTO_MAX_BYTES),
        )
    }

    private fun load() = MyCard(
        name = prefs.getString(K_NAME, "") ?: "",
        phone = prefs.getString(K_PHONE, "") ?: "",
        email = prefs.getString(K_EMAIL, "") ?: "",
        sharePhone = prefs.getBoolean(K_SHARE_PHONE, true),
        shareEmail = prefs.getBoolean(K_SHARE_EMAIL, false),
        photoVersion = if (photoFile.exists()) prefs.getLong(K_PHOTO_VERSION, 0L) else 0L,
    )

    private fun centerCrop(src: Bitmap, size: Int): Bitmap {
        val side = min(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return if (side == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
    }

    companion object {
        const val PHOTO_PX = 512

        /** Sent in Hello (who is this?) — tiny. */
        const val AVATAR_PX = 96
        const val AVATAR_MAX_BYTES = 8 * 1024

        /** Sent with the card after Share — good enough for a contact photo. */
        const val CARD_PHOTO_PX = 256
        const val CARD_PHOTO_MAX_BYTES = 22 * 1024

        private const val K_NAME = "name"
        private const val K_PHONE = "phone"
        private const val K_EMAIL = "email"
        private const val K_SHARE_PHONE = "share_phone"
        private const val K_SHARE_EMAIL = "share_email"
        private const val K_PHOTO_VERSION = "photo_version"
    }
}
