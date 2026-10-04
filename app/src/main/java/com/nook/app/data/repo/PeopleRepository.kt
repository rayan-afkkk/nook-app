package com.nook.app.data.repo

import com.nook.app.data.model.Chat
import com.nook.app.data.model.Presence
import com.nook.app.data.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class Contact(val user: User, val presence: Presence, val chatId: String, val lastMessageAt: Long)

/** "People I chat with": everyone I have a DM with, live with presence, newest first. */
class PeopleRepository(
    private val chats: ChatRepository,
    private val users: UserRepository,
    private val realtime: RealtimeRepository,
    private val auth: AuthRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val contacts: Flow<List<Contact>> = chats.chatList.filterNotNull().flatMapLatest { list ->
        val me = auth.uid ?: return@flatMapLatest flowOf(emptyList())
        val dms = list.filter { !it.isGroup }.mapNotNull { chat -> chat.otherMember(me)?.let { it to chat } }
        if (dms.isEmpty()) flowOf(emptyList())
        else combine(dms.map { (uid, chat) -> contactFlow(uid, chat) }) { arr -> arr.filterNotNull().sortedByDescending { it.lastMessageAt } }
    }

    private fun contactFlow(uid: String, chat: Chat): Flow<Contact?> =
        combine(users.observeUser(uid), realtime.observePresence(uid)) { u, p ->
            u?.let { Contact(it, p, chat.id, chat.lastMessageAt) }
        }
}
