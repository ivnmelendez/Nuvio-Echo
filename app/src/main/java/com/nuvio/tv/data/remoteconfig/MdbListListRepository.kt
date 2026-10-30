package com.nuvio.tv.data.remoteconfig

import android.content.Context
import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the rows precomputed by the importer (MDBList list already filtered to titles that exist
 * in the library) from the same repo as the config. No MDBList or VPS calls from the device.
 */
@Singleton
class MdbListListRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private data class Entry(val items: List<MetaPreview>, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val sessionSeed = kotlin.random.Random.nextLong()
    private val baseUrl: String get() = BuildConfig.REMOTE_CONFIG_URL.substringBeforeLast('/') + "/"

    suspend fun items(row: RemoteRow): List<MetaPreview> = withContext(Dispatchers.IO) {
        val file = row.file ?: return@withContext emptyList()
        cache[file]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }
            ?.let { return@withContext order(it.items, row) }

        val diskFile = File(context.filesDir, "remote_rows/" + file.replace('/', '_'))
        val body = runCatching { fetch(baseUrl + file) }
            .onFailure { Log.w(TAG, "Row $file fetch failed: ${it.message}") }
            .getOrNull()
            ?.also { runCatching { diskFile.parentFile?.mkdirs(); diskFile.writeText(it) } }
            ?: runCatching { diskFile.takeIf { it.exists() }?.readText() }.getOrNull()
            ?: return@withContext emptyList()

        val items = runCatching { parse(body) }
            .onFailure { Log.w(TAG, "Row $file parse failed: ${it.message}") }
            .getOrDefault(emptyList())
        if (items.isNotEmpty()) cache[file] = Entry(items, System.currentTimeMillis() + CACHE_TTL_MS)
        order(items, row)
    }

    /** Items from memory or disk only, ignoring age; empty when never fetched. No network. */
    suspend fun cachedItems(row: RemoteRow): List<MetaPreview> = withContext(Dispatchers.IO) {
        val file = row.file ?: return@withContext emptyList()
        cache[file]?.let { return@withContext order(it.items, row) }
        val diskFile = File(context.filesDir, "remote_rows/" + file.replace('/', '_'))
        val items = runCatching { diskFile.takeIf { it.exists() }?.readText()?.let(::parse) }.getOrNull().orEmpty()
        if (items.isNotEmpty()) cache[file] = Entry(items, 0L)
        order(items, row)
    }

    /**
     * The same shuffle for the whole app session, a new one each time the app opens. Shuffling on every read gave
     * Home a different row each time it was built (coming back from a title, and again when the fresh copy
     * arrived), so the card a person was on was gone and focus fell to the menu.
     */
    private fun shuffled(items: List<MetaPreview>, row: RemoteRow): List<MetaPreview> =
        items.shuffled(kotlin.random.Random(sessionSeed xor (row.file?.hashCode()?.toLong() ?: 0L)))

    private fun order(items: List<MetaPreview>, row: RemoteRow): List<MetaPreview> = when {
        // The file holds a larger pool; show a different `limit`-sized pick each time the app opens.
        row.shuffle -> shuffled(items, row).take(row.limit)
        row.sort.equals("random", ignoreCase = true) -> shuffled(items, row)
        else -> items
    }

    private fun fetch(url: String): String? {
        val bust = url + (if ('?' in url) "&" else "?") + "t=" + (System.currentTimeMillis() / 60_000L)
        val request = Request.Builder().url(bust).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun parse(body: String): List<MetaPreview> {
        val array = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val imdb = o.optString("id").ifBlank { null } ?: continue
                val type = ContentType.fromString(o.optString("type", "movie"))
                add(
                    MetaPreview(
                        id = imdb,
                        type = type,
                        name = o.optString("title").ifBlank { imdb },
                        poster = "https://images.metahub.space/poster/medium/$imdb/img",
                        posterShape = PosterShape.POSTER,
                        background = "https://images.metahub.space/background/medium/$imdb/img",
                        logo = null,
                        description = null,
                        releaseInfo = o.optInt("year", 0).takeIf { it > 0 }?.toString(),
                        imdbRating = null,
                        genres = emptyList(),
                        imdbId = imdb
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "MdbListListRepository"
        const val CACHE_TTL_MS = 10L * 60L * 1000L
    }
}
