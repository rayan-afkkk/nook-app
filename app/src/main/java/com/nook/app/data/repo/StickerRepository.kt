package com.nook.app.data.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nook.app.data.firebase.Fields
import com.nook.app.data.firebase.snapshots
import com.nook.app.data.firebase.toStickerPack
import com.nook.app.data.model.Chat
import com.nook.app.data.model.Media
import com.nook.app.data.model.StickerPack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** stickerPacks/{packId}: custom packs made from photos, shared with a group's members. */
class StickerRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
) {
    private val packs get() = db.collection(Fields.STICKER_PACKS)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun myPacks(): Flow<List<StickerPack>> = auth.authState.flatMapLatest { u ->
        if (u == null) flowOf(emptyList())
        else packs.whereArrayContains("members", u.uid).limit(50).snapshots()
            .map { s -> s.documents.mapNotNull { it.toStickerPack() } }
            .catch { emit(emptyList()) }
    }

    fun observe(packId: String): Flow<StickerPack?> = packs.document(packId).snapshots().map { it.toStickerPack() }

    suspend fun create(chat: Chat, name: String): String {
        val ref = packs.document()
        ref.set(
            mapOf(
                "chatId" to chat.id,
                "name" to name.trim().take(30),
                "createdBy" to auth.requireUid(),
                "members" to chat.members,
                "stickers" to emptyList<Map<String, String>>(),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return ref.id
    }

    suspend fun addSticker(packId: String, media: Media) {
        packs.document(packId).update(
            "stickers",
            FieldValue.arrayUnion(mapOf("url" to media.url, "publicId" to (media.publicId ?: ""))),
        ).await()
    }

    suspend fun delete(packId: String) { packs.document(packId).delete().await() }
}
