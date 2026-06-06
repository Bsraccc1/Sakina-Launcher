package app.olauncher.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.util.Locale

object PrayerLocationHelper {
    data class DetectedLocation(
        val cityQuery: String,
        val country: String,
        val latitude: Double,
        val longitude: Double,
        val timeZoneId: String,
    )

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    suspend fun detectCityQuery(context: Context): String? = withContext(Dispatchers.IO) {
        detectLocation(context)?.cityQuery
    }

    suspend fun detectLocation(context: Context): DetectedLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        val location = lastKnownLocation(context) ?: currentLocation(context) ?: return@withContext null
        val address = reverseGeocodeAddress(context, location)
        val cityQuery = listOf(address?.subAdminArea, address?.locality, address?.adminArea)
            .firstOrNull { it.isNullOrBlank().not() }
            ?: return@withContext null
        DetectedLocation(
            cityQuery = cityQuery,
            country = address?.countryName?.ifBlank { null } ?: Locale.getDefault().displayCountry,
            latitude = location.latitude,
            longitude = location.longitude,
            timeZoneId = java.util.TimeZone.getDefault().id,
        )
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            }.getOrNull()
        }.maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                    .firstOrNull { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
                if (provider == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                        manager.removeUpdates(this)
                    }

                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                runCatching {
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun reverseGeocodeCity(context: Context, location: Location): String? {
        val address = reverseGeocodeAddress(context, location)
        return listOf(address?.subAdminArea, address?.locality, address?.adminArea)
            .firstOrNull { it.isNullOrBlank().not() }
    }

    private fun reverseGeocodeAddress(context: Context, location: Location): android.location.Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        return runCatching {
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        }.getOrNull()
    }

    private const val LOCATION_TIMEOUT_MS = 5_000L
}
