package com.aniket.tapshare.handshake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionMessagesTest {
    private fun roundTrip(m: SessionMessage) = assertEquals(m, SessionCodec.decode(SessionCodec.encode(m)))

    @Test
    fun allMessagesRoundTrip() {
        roundTrip(
            SessionMessage.Hello(
                name = "Aniket Raj",
                avatarJpeg = byteArrayOf(1, 2, 3),
                mode = SessionMode.SEND,
                items = listOf(ItemInfo("IMG_001.jpg", "image/jpeg", 2_345_678), ItemInfo("नोट्स.pdf", "application/pdf", 10)),
                previewJpeg = null,
            ),
        )
        roundTrip(SessionMessage.Hello("A", null, SessionMode.CONTACT, emptyList(), null))
        roundTrip(SessionMessage.Card(ContactCard("Aniket", "+91 98765 43210", null, byteArrayOf(9, 9))))
        roundTrip(SessionMessage.Card(ContactCard("Aniket", null, "a@b.c", null)))
        roundTrip(SessionMessage.ReceiveOnly)
        roundTrip(SessionMessage.Accept)
        roundTrip(SessionMessage.Decline)
        roundTrip(SessionMessage.FileHeader(123456789L, 2, "video.mp4", "video/mp4", 99_000_000))
        roundTrip(SessionMessage.Cancel)
    }

    @Test(expected = IOException::class)
    fun rejectsOtherVersion() {
        val bytes = SessionCodec.encode(SessionMessage.Accept)
        bytes[0] = 99
        SessionCodec.decode(bytes)
    }

    @Test(expected = IOException::class)
    fun rejectsGarbage() {
        SessionCodec.decode(byteArrayOf(1, 42))
    }

    @Test
    fun rolesAreOpposite() {
        assertTrue(iAmAdvertiser("0A", "0B"))
        assertFalse(iAmAdvertiser("0B", "0A"))
    }

    @Test
    fun describesItems() {
        assertEquals(ItemKind.PHOTOS, describeItems(listOf(ItemInfo("a", "image/png", 1), ItemInfo("b", "image/jpeg", 2))).kind)
        assertEquals(ItemKind.VIDEOS, describeItems(listOf(ItemInfo("a", "video/mp4", 1))).kind)
        assertEquals(ItemKind.ITEMS, describeItems(listOf(ItemInfo("a", "video/mp4", 1), ItemInfo("b", "image/png", 1))).kind)
        val files = describeItems(listOf(ItemInfo("a", "application/pdf", 5), ItemInfo("b", "image/png", 7)))
        assertEquals(ItemKind.FILES, files.kind)
        assertEquals(2, files.count)
        assertEquals(12, files.totalBytes)
    }
}
