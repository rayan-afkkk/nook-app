package com.nook.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.nook.app.AppConfig
import com.nook.app.data.media.ImageCompressor
import com.nook.app.data.model.Media
import com.nook.app.data.remote.CloudinaryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileTooLargeException : Exception("Files can be up to 10 MB")

/**
 * Everything binary goes to Cloudinary, foldered so the Worker can authorise deletes:
 *   nook/chats/{chatId}/…   message media & group stickers
 *   nook/avatars/{uid}/…    profile pictures
 */
class MediaRepository(
    private val context: Context,
    private val cloudinary: CloudinaryApi,
) {
    fun chatFolder(chatId: String) = "nook/chats/$chatId"
    fun avatarFolder(uid: String) = "nook/avatars/$uid"

    suspend fun uploadImage(uri: Uri, folder: String, onProgress: (Float) -> Unit = {}): Media = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        if (mime == "image/gif") return@withContext uploadFile(uri, folder, onProgress)
        val img = ImageCompressor.compress(context, uri)
        val res = cloudinary.upload(img.bytes, "${UUID.randomUUID()}.jpg", "image/jpeg", folder, onProgress)
        Media(
            url = res.secureUrl, publicId = res.publicId, resourceType = res.resourceType, mime = "image/jpeg",
            size = res.bytes, width = img.width, height = img.height,
        )
    }

    suspend fun uploadBitmap(bitmap: Bitmap, folder: String, onProgress: (Float) -> Unit = {}): Media = withContext(Dispatchers.IO) {
        val img = ImageCompressor.encode(bitmap, 90)
        val res = cloudinary.upload(img.bytes, "${UUID.randomUUID()}.jpg", "image/jpeg", folder, onProgress)
        Media(url = res.secureUrl, publicId = res.publicId, resourceType = res.resourceType, mime = "image/jpeg", width = img.width, height = img.height)
    }

    suspend fun uploadFile(uri: Uri, folder: String, onProgress: (Float) -> Unit = {}): Media = withContext(Dispatchers.IO) {
        val (name, size) = describe(uri)
        if (size > AppConfig.MAX_FILE_BYTES) throw FileTooLargeException()
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read that file")
        if (bytes.size > AppConfig.MAX_FILE_BYTES) throw FileTooLargeException()
        val res = cloudinary.upload(bytes, name, mime, folder, onProgress)
        Media(
            url = res.secureUrl, publicId = res.publicId, resourceType = res.resourceType, mime = mime,
            name = name, size = bytes.size.toLong(), width = res.width, height = res.height,
        )
    }

    suspend fun uploadVoice(file: File, durationMs: Long, waveform: List<Float>, folder: String, onProgress: (Float) -> Unit = {}): Media =
        withContext(Dispatchers.IO) {
            val bytes = file.readBytes()
            val res = cloudinary.upload(bytes, file.name, "audio/mp4", folder, onProgress)
            Media(
                url = res.secureUrl, publicId = res.publicId, resourceType = res.resourceType, mime = "audio/mp4",
                size = bytes.size.toLong(), durationMs = durationMs, waveform = waveform,
            )
        }

    fun describe(uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        return name to size
    }
}
