package com.kukurigu.sunalarm.data

import kotlinx.serialization.Serializable

/**
 * A geographic place the user can pick instead of typing raw coordinates.
 *
 * Comes from two sources that share this type: the bundled offline catalogue
 * ([CityCatalog]) and the on-device [GeocodingService]. Only the latitude and
 * longitude actually feed the solar math; [name]/[admin]/[country] are for display.
 */
@Serializable
data class City(
    val name: String,
    val country: String? = null,
    val admin: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** Concise label stored on the chosen [AppLocation], e.g. "Berlin, Germany". */
    val displayName: String
        get() {
            val tail = country?.takeIf { it.isNotBlank() } ?: admin?.takeIf { it.isNotBlank() }
            return if (tail != null && !tail.equals(name, ignoreCase = true)) "$name, $tail" else name
        }

    /** Secondary line for search rows, e.g. "England, United Kingdom" (null if empty). */
    val secondaryLine: String?
        get() = listOfNotNull(
            admin?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
            country?.takeIf { it.isNotBlank() },
        ).distinct().joinToString(", ").ifBlank { null }
}
