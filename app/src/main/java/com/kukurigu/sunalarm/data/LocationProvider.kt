package com.kukurigu.sunalarm.data

import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's last known location using the Android framework
 * [LocationManager] only — deliberately no Google Play Services dependency.
 *
 * The UI must always allow manual latitude/longitude entry so the app remains
 * fully usable when location permission is denied or no fix is available.
 */
class LocationProvider(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Returns the most recent cached fix across the GPS, network and passive
     * providers, mapped to an [AppLocation], or null if none is available or the
     * location permission has not been granted.
     */
    suspend fun current(): AppLocation? = withContext(Dispatchers.IO) {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        var best: Location? = null
        for (provider in providers) {
            val candidate: Location? = try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                // Permission not granted for this provider.
                null
            } catch (e: IllegalArgumentException) {
                // Provider not present on this device.
                null
            }
            if (candidate != null && (best == null || candidate.time > best!!.time)) {
                best = candidate
            }
        }

        best?.let {
            AppLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                label = "Current location",
            )
        }
    }
}
