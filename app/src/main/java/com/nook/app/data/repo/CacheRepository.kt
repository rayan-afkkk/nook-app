package com.nook.app.data.repo

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageInfo(val mediaCacheBytes: Long, val offlineDataBytes: Long)

/** Powers the Account → device card. */
class CacheRepository(private val context: Context) {

    suspend fun info(): StorageInfo = withContext(Dispatchers.IO) {
        val media = dirSize(context.cacheDir)
        val dbDir = context.getDatabasePath("x").parentFile
        val offline = dbDir?.listFiles()?.filter { it.name.startsWith("firestore") }?.sumOf { it.length() } ?: 0L
        StorageInfo(media, offline)
    }

    suspend fun clearMediaCache() = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun dirSize(f: File): Long =
        if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

    companion object {
        const val MEDIA_CACHE_BUDGET = 250L * 1024 * 1024
        const val OFFLINE_BUDGET = 100L * 1024 * 1024
    }
}
