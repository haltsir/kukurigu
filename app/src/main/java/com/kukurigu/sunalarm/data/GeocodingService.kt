package com.kukurigu.sunalarm.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * City lookup backed by the Android framework [Geocoder] — no Google Play
 * Services dependency. Forward search turns a typed name into [City] results;
 * reverse lookup turns a GPS fix into a readable place label.
 *
 * The geocoder needs a backend (usually network) and may be absent on some
 * devices/emulators; in that case methods return empty/null and the caller
 * falls back to the bundled [CityCatalog].
 */
class GeocodingService(context: Context) {

    private val appContext = context.applicationContext

    /** Searches for places matching [query]; empty if no geocoder backend or no match. */
    suspend fun search(query: String): List<City> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(appContext, Locale.getDefault())
        return geocodeByName(geocoder, query).mapNotNull { it.toCity() }
    }

    /** Returns a human-readable label for a coordinate, or null if unavailable. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.getDefault())
        return geocodeByLocation(geocoder, latitude, longitude).firstOrNull()?.toCity()?.displayName
    }

    // ---------------------------------------------------------------------
    // Geocoder API: async listener on API 33+, blocking (off main thread) below.
    // ---------------------------------------------------------------------

    private suspend fun geocodeByName(geocoder: Geocoder, query: String): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodeByNameAsync(geocoder, query)
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(query, MAX_RESULTS) }
                    .getOrNull().orEmpty()
            }
        }

    private suspend fun geocodeByLocation(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodeByLocationAsync(geocoder, latitude, longitude)
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1) }
                    .getOrNull().orEmpty()
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeByNameAsync(geocoder: Geocoder, query: String): List<Address> =
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocationName(
                query,
                MAX_RESULTS,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                },
            )
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeByLocationAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(
            latitude,
            longitude,
            1,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (cont.isActive) cont.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            },
        )
    }

    private fun Address.toCity(): City? {
        if (!hasLatitude() || !hasLongitude()) return null
        val placeName = locality ?: subAdminArea ?: featureName ?: adminArea ?: return null
        return City(
            name = placeName,
            country = countryName,
            admin = adminArea?.takeIf { !it.equals(placeName, ignoreCase = true) },
            latitude = latitude,
            longitude = longitude,
        )
    }

    private companion object {
        const val MAX_RESULTS = 6
    }
}
