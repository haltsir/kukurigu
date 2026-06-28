package com.kukurigu.sunalarm.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Offline catalogue of major world cities bundled as an asset (`cities.json`).
 *
 * Provides instant, network-free search so the city picker works on devices or
 * emulators without a geocoder backend. [GeocodingService] supplements this with
 * full online coverage when available.
 */
class CityCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var cities: List<City>? = null

    private suspend fun all(): List<City> {
        cities?.let { return it }
        return mutex.withLock {
            cities ?: withContext(Dispatchers.IO) {
                runCatching {
                    val text = appContext.assets.open(ASSET_NAME)
                        .bufferedReader()
                        .use { it.readText() }
                    json.decodeFromString<List<City>>(text)
                }.getOrDefault(emptyList())
            }.also { cities = it }
        }
    }

    /**
     * Returns up to [LIMIT] catalogue cities matching [query] (case-insensitive),
     * prefix matches first, then substring matches.
     */
    suspend fun search(query: String): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val all = all()
        val prefix = all.filter { it.name.lowercase().startsWith(q) }
        val contains = all.filter {
            val lower = it.name.lowercase()
            lower.contains(q) && !lower.startsWith(q)
        }
        return (prefix + contains).take(LIMIT)
    }

    private companion object {
        const val ASSET_NAME = "cities.json"
        const val LIMIT = 20
    }
}
