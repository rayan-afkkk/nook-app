package com.nook.app.data

import com.nook.app.data.media.VoiceRecorder
import com.nook.app.data.model.Chat
import com.nook.app.data.model.ChatType
import com.nook.app.data.model.Disappearing
import com.nook.app.data.model.LastMessage
import com.nook.app.data.model.MessageType
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.UsernameRules
import com.nook.app.feature.chat.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesAndModelsTest {

    @Test fun `usernames mirror the server regex`() {
        assertNull(UsernameRules.validate("ali_k.99"))
        assertEquals("At least 3 characters", UsernameRules.validate("al"))
        assertEquals("Only a–z, 0–9, _ and .", UsernameRules.validate("ali-k"))
        assertEquals("Can't start or end with a dot", UsernameRules.validate(".ali"))
        assertEquals("No double dots", UsernameRules.validate("a..li"))
        assertEquals("ali", UsernameRules.normalize("  @Ali "))
    }

    @Test fun `dm ids are deterministic regardless of order`() {
        assertEquals(ChatRepository.dmId("b", "a"), ChatRepository.dmId("a", "b"))
        assertEquals("dm_a_b", ChatRepository.dmId("b", "a"))
    }

    private fun chat(lastSender: String?, lastAt: Long, myRead: Long) = Chat(
        id = "c", type = ChatType.DIRECT, members = listOf("me", "you"), name = null, photoUrl = null,
        createdBy = "me", createdAt = 0, lastMessageAt = lastAt,
        lastMessage = lastSender?.let { LastMessage("m", it, MessageType.TEXT, "hi") },
        lastRead = mapOf("me" to myRead), disappearing = Disappearing.OFF,
    )

    @Test fun `unread is lastMessageAt vs my lastRead, never for my own messages`() {
        assertTrue(chat("you", lastAt = 200, myRead = 100).isUnread("me"))
        assertFalse(chat("you", lastAt = 100, myRead = 200).isUnread("me"))
        assertFalse(chat("me", lastAt = 300, myRead = 100).isUnread("me"))
        assertFalse(chat(null, lastAt = 300, myRead = 0).isUnread("me"))
    }

    @Test fun `typing labels read naturally`() {
        assertEquals("Ali is typing…", ChatViewModel.typingLabel(listOf("Ali")))
        assertEquals("Ali and Sam are typing…", ChatViewModel.typingLabel(listOf("Ali", "Sam")))
        assertEquals("3 people are typing…", ChatViewModel.typingLabel(listOf("A", "B", "C")))
    }

    @Test fun `voice waveform is downsampled to a fixed number of bars`() {
        val bars = VoiceRecorder.downsample(List(500) { (it % 10) / 10f }, 48)
        assertEquals(48, bars.size)
        assertTrue(bars.all { it in 0.06f..1f })
        assertEquals(48, VoiceRecorder.downsample(emptyList(), 48).size)
    }

    @Test fun `disappearing timers map to the wire format`() {
        assertEquals(Disappearing.DAY, Disappearing.from("24h"))
        assertEquals(Disappearing.OFF, Disappearing.from("garbage"))
        assertEquals(7L * 24 * 3600 * 1000, Disappearing.WEEK.millis)
    }
}
