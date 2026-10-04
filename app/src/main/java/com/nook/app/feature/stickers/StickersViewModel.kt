package com.nook.app.feature.stickers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.model.Chat
import com.nook.app.data.model.GiphyItem
import com.nook.app.data.model.GiphyKind
import com.nook.app.data.model.Media
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.Sticker
import com.nook.app.data.model.StickerPack
import com.nook.app.data.remote.GiphyApi
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.MessageRepository
import com.nook.app.data.repo.OutgoingMessage
import com.nook.app.data.repo.Outbox
import com.nook.app.data.repo.Payload
import com.nook.app.data.repo.StickerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StickersViewModel(
    private val handle: SavedStateHandle,
    api: GiphyApi,
    private val stickers: StickerRepository,
    private val chats: ChatRepository,
    private val messages: MessageRepository,
    private val outbox: Outbox,
) : ViewModel() {
    val giphy = GiphyBrowser(api, viewModelScope)
    val tab: StateFlow<Int> = handle.getStateFlow("tab", 1)
    val packs: StateFlow<List<StickerPack>?> = stickers.myPacks().map<List<StickerPack>, List<StickerPack>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val groups: StateFlow<List<Chat>> = chats.chatList.map { it.orEmpty().filter { c -> c.isGroup } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init { applyTab(tab.value) }

    fun setTab(t: Int) { handle["tab"] = t; applyTab(t) }

    private fun applyTab(t: Int) {
        when (t) {
            0 -> giphy.start(GiphyKind.GIFS)
            1 -> giphy.start(GiphyKind.STICKERS)
        }
    }

    fun category(q: String) { giphy.setQuery(q) }

    private fun sendTo(chatIds: List<String>, build: (chatId: String) -> OutgoingMessage) {
        viewModelScope.launch {
            val all = chats.chatList.filterNotNull().first().associateBy { it.id }
            chatIds.mapNotNull { all[it] }.forEach { chat -> outbox.send(chat, Payload.Ready(build(chat.id))) }
            _toast.value = if (chatIds.size == 1) "Sent" else "Sent to ${chatIds.size} chats"
        }
    }

    fun sendGiphy(item: GiphyItem, sticker: Boolean, chatIds: List<String>) = sendTo(chatIds) { id ->
        OutgoingMessage(
            messages.newId(id), if (sticker) MessageType.STICKER else MessageType.GIF,
            media = Media(url = item.url, width = item.width, height = item.height, mime = "image/webp"),
        )
    }

    fun sendPackSticker(s: Sticker, chatIds: List<String>) = sendTo(chatIds) { id ->
        OutgoingMessage(messages.newId(id), MessageType.STICKER, media = Media(url = s.url, width = 512, height = 512))
    }

    fun createPack(group: Chat, name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { stickers.create(group, name.ifBlank { "${group.name ?: "Group"} stickers" }) }
                .onSuccess(onCreated)
                .onFailure { _toast.value = it.message }
        }
    }

    fun clearToast() { _toast.value = null }
}
