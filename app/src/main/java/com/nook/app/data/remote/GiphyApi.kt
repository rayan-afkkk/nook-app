package com.nook.app.data.remote

import com.nook.app.AppConfig
import com.nook.app.data.model.GiphyItem
import com.nook.app.data.model.GiphyKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Plain REST client for Giphy (lighter than the Giphy SDK). */
class GiphyApi(private val client: OkHttpClient, private val json: Json) {

    suspend fun fetch(kind: GiphyKind, query: String?, offset: Int = 0, limit: Int = 24): List<GiphyItem> {
        check(AppConfig.hasGiphy) { "Giphy isn't configured yet (GIPHY_API_KEY)." }
        val type = if (kind == GiphyKind.GIFS) "gifs" else "stickers"
        val endpoint = if (query.isNullOrBlank()) "trending" else "search"
        val url = "https://api.giphy.com/v1/$type/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", AppConfig.giphyKey)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("rating", "pg-13")
            .apply { if (!query.isNullOrBlank()) addQueryParameter("q", query) }
            .build()
        client.newCall(Request.Builder().url(url).get().build()).await().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code, "Giphy failed (${res.code})")
            val root = json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject
            return root["data"]?.jsonArray.orEmpty().mapNotNull { el -> parse(el.jsonObject) }
        }
    }

    private fun parse(o: JsonObject): GiphyItem? {
        val images = o["images"]?.jsonObject ?: return null
        fun img(name: String) = images[name]?.jsonObject
        val full = img("fixed_width") ?: img("downsized") ?: return null
        val preview = img("fixed_width_small") ?: full
        fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
        val url = full.str("webp") ?: full.str("url") ?: return null
        return GiphyItem(
            id = o.str("id") ?: return null,
            title = o.str("title").orEmpty(),
            previewUrl = preview.str("webp") ?: preview.str("url") ?: url,
            url = url,
            width = full.str("width")?.toIntOrNull() ?: 200,
            height = full.str("height")?.toIntOrNull() ?: 200,
        )
    }
}
