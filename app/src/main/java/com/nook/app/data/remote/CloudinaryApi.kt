package com.nook.app.data.remote

import com.nook.app.AppConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

@Serializable
data class CloudinaryResponse(
    @SerialName("secure_url") val secureUrl: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("resource_type") val resourceType: String = "image",
    val bytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0,
    val format: String? = null,
)

/**
 * Unsigned uploads to Cloudinary (Firebase Storage isn't available on the Spark plan).
 * The preset restricts formats, size and folder — configure it as described in the README.
 */
class CloudinaryApi(private val client: OkHttpClient, private val json: Json) {

    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mime: String,
        folder: String,
        onProgress: (Float) -> Unit = {},
    ): CloudinaryResponse {
        check(AppConfig.hasCloudinary) { "Cloudinary isn't configured yet (see README → Cloudinary)." }
        val fileBody = ProgressBody(RequestBodyBytes(bytes, mime.toMediaTypeOrNull()), onProgress)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", AppConfig.cloudinaryPreset)
            .addFormDataPart("folder", folder)
            .addFormDataPart("file", fileName, fileBody)
            .build()
        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/${AppConfig.cloudinaryCloud}/auto/upload")
            .post(body)
            .build()
        client.newCall(request).await().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, "Upload failed (${res.code})")
            return json.decodeFromString(CloudinaryResponse.serializer(), text)
        }
    }

    private class RequestBodyBytes(private val bytes: ByteArray, private val type: MediaType?) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
    }

    private class ProgressBody(private val delegate: RequestBody, private val onProgress: (Float) -> Unit) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun writeTo(sink: BufferedSink) {
            val total = contentLength().coerceAtLeast(1)
            var written = 0L
            val counting = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    written += byteCount
                    onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }.buffer()
            delegate.writeTo(counting)
            counting.flush()
        }
    }
}
