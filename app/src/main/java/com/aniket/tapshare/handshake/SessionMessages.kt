package com.aniket.tapshare.handshake

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

// Pure Kotlin/Java (no android.* imports) so it runs in plain JVM unit tests.

/** What this phone is doing in a session. */
enum class SessionMode {
    /** Nothing queued: NameDrop-style contact exchange. */
    CONTACT,

    /** Photos/files queued (share sheet or picker): AirDrop-style send. */
    SEND,
}

/** One photo/file being offered (no content yet — just what the receiver sees before accepting). */
data class ItemInfo(val name: String, val mime: String, val size: Long)

/** Shared only after the user taps Share. Fields the user switched off are null. */
data class ContactCard(
    val name: String,
    val phone: String?,
    val email: String?,
    val photoJpeg: ByteArray?,
) {
    override fun equals(other: Any?): Boolean = other is ContactCard &&
        name == other.name && phone == other.phone && email == other.email && bytesEq(photoJpeg, other.photoJpeg)

    override fun hashCode(): Int = name.hashCode() * 31 + (phone?.hashCode() ?: 0)
}

/**
 * Messages sent as small BYTES payloads over Nearby Connections (already encrypted by Nearby).
 * Bulk data (photos/files) goes as FILE payloads, announced first by [FileHeader].
 */
sealed interface SessionMessage {
    /**
     * First message after connecting. Like AirDrop, the other phone sees who it's talking to
     * (display name + small photo). Phone/email are NOT here — only in [Card] after Share.
     */
    data class Hello(
        val name: String,
        val avatarJpeg: ByteArray?,
        val mode: SessionMode,
        val items: List<ItemInfo>,
        val previewJpeg: ByteArray?,
    ) : SessionMessage {
        override fun equals(other: Any?): Boolean = other is Hello && name == other.name && mode == other.mode &&
            items == other.items && bytesEq(avatarJpeg, other.avatarJpeg) && bytesEq(previewJpeg, other.previewJpeg)

        override fun hashCode(): Int = name.hashCode() * 31 + items.hashCode()
    }

    /** "Share" tapped on the contact poster. */
    data class Card(val card: ContactCard) : SessionMessage

    /** "Receive Only" tapped: I won't send my card. */
    data object ReceiveOnly : SessionMessage

    /** Receiver accepted the offered items. */
    data object Accept : SessionMessage

    /** Receiver declined the offered items. */
    data object Decline : SessionMessage

    /** Announces the FILE payload [payloadId] before it arrives, with its real name and type. */
    data class FileHeader(val payloadId: Long, val index: Int, val name: String, val mime: String, val size: Long) :
        SessionMessage

    /** Either side gave up (Cancel button, or an error). */
    data object Cancel : SessionMessage
}

private fun bytesEq(a: ByteArray?, b: ByteArray?): Boolean =
    if (a == null || b == null) a == null && b == null else a.contentEquals(b)

object SessionCodec {
    /** Bump when the format changes; older phones then show "Update TapShare on both phones". */
    const val VERSION = 1

    /** Nearby's BYTES payload limit is small; we stay well under it. */
    const val MAX_MESSAGE_BYTES = 30 * 1024

    private const val T_HELLO = 1
    private const val T_CARD = 2
    private const val T_RECEIVE_ONLY = 3
    private const val T_ACCEPT = 4
    private const val T_DECLINE = 5
    private const val T_FILE_HEADER = 6
    private const val T_CANCEL = 7

    fun encode(message: SessionMessage): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeByte(VERSION)
            when (message) {
                is SessionMessage.Hello -> {
                    out.writeByte(T_HELLO)
                    out.writeUTF(message.name)
                    out.writeBytesOrNull(message.avatarJpeg)
                    out.writeByte(message.mode.ordinal)
                    out.writeInt(message.items.size)
                    for (item in message.items) {
                        out.writeUTF(item.name)
                        out.writeUTF(item.mime)
                        out.writeLong(item.size)
                    }
                    out.writeBytesOrNull(message.previewJpeg)
                }
                is SessionMessage.Card -> {
                    out.writeByte(T_CARD)
                    out.writeUTF(message.card.name)
                    out.writeStringOrNull(message.card.phone)
                    out.writeStringOrNull(message.card.email)
                    out.writeBytesOrNull(message.card.photoJpeg)
                }
                SessionMessage.ReceiveOnly -> out.writeByte(T_RECEIVE_ONLY)
                SessionMessage.Accept -> out.writeByte(T_ACCEPT)
                SessionMessage.Decline -> out.writeByte(T_DECLINE)
                is SessionMessage.FileHeader -> {
                    out.writeByte(T_FILE_HEADER)
                    out.writeLong(message.payloadId)
                    out.writeInt(message.index)
                    out.writeUTF(message.name)
                    out.writeUTF(message.mime)
                    out.writeLong(message.size)
                }
                SessionMessage.Cancel -> out.writeByte(T_CANCEL)
            }
        }
        return buffer.toByteArray()
    }

    /** Throws [IOException] for garbage or a newer, unknown format. */
    fun decode(bytes: ByteArray): SessionMessage {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readUnsignedByte()
            if (version != VERSION) throw IOException("Unsupported TapShare protocol version $version")
            return when (val type = input.readUnsignedByte()) {
                T_HELLO -> {
                    val name = input.readUTF()
                    val avatar = input.readBytesOrNull()
                    val mode = SessionMode.entries.getOrNull(input.readUnsignedByte())
                        ?: throw IOException("Bad mode")
                    val count = input.readInt()
                    if (count !in 0..10_000) throw IOException("Bad item count $count")
                    val items = List(count) { ItemInfo(input.readUTF(), input.readUTF(), input.readLong()) }
                    SessionMessage.Hello(name, avatar, mode, items, input.readBytesOrNull())
                }
                T_CARD -> SessionMessage.Card(
                    ContactCard(
                        name = input.readUTF(),
                        phone = input.readStringOrNull(),
                        email = input.readStringOrNull(),
                        photoJpeg = input.readBytesOrNull(),
                    ),
                )
                T_RECEIVE_ONLY -> SessionMessage.ReceiveOnly
                T_ACCEPT -> SessionMessage.Accept
                T_DECLINE -> SessionMessage.Decline
                T_FILE_HEADER -> SessionMessage.FileHeader(
                    payloadId = input.readLong(),
                    index = input.readInt(),
                    name = input.readUTF(),
                    mime = input.readUTF(),
                    size = input.readLong(),
                )
                T_CANCEL -> SessionMessage.Cancel
                else -> throw IOException("Unknown message type $type")
            }
        }
    }

    private fun DataOutputStream.writeBytesOrNull(bytes: ByteArray?) {
        if (bytes == null) {
            writeInt(-1)
        } else {
            writeInt(bytes.size)
            write(bytes)
        }
    }

    private fun DataInputStream.readBytesOrNull(): ByteArray? {
        val size = readInt()
        if (size == -1) return null
        if (size !in 0..MAX_MESSAGE_BYTES) throw IOException("Bad byte length $size")
        return ByteArray(size).also { readFully(it) }
    }

    private fun DataOutputStream.writeStringOrNull(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readStringOrNull(): String? = if (readBoolean()) readUTF() else null
}

/**
 * Which phone advertises and which one discovers on Nearby Connections. Both phones compute this
 * from the two random BLE IDs, so they always pick opposite roles without talking first.
 */
fun iAmAdvertiser(myId: String, peerId: String): Boolean = myId < peerId

/** Human summary for the receiver: "3 photos", "1 video", "2 files", "4 items". */
fun describeItems(items: List<ItemInfo>): ItemsSummary {
    val kind = when {
        items.isEmpty() -> ItemKind.ITEMS
        items.all { it.mime.startsWith("image/") } -> ItemKind.PHOTOS
        items.all { it.mime.startsWith("video/") } -> ItemKind.VIDEOS
        items.all { it.mime.startsWith("image/") || it.mime.startsWith("video/") } -> ItemKind.ITEMS
        else -> ItemKind.FILES
    }
    return ItemsSummary(kind, items.size, items.sumOf { it.size.coerceAtLeast(0) })
}

enum class ItemKind { PHOTOS, VIDEOS, FILES, ITEMS }

data class ItemsSummary(val kind: ItemKind, val count: Int, val totalBytes: Long)
