package ai.daylight.assistant.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import ai.daylight.assistant.data.preferences.AppPreferences
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.google.android.gms.location.LocationServices
import kotlin.coroutines.resume

/**
 * Coarse device location, cached locally and only used to give the assistant regional
 * context (e.g. searches near the user). Nothing leaves the device except as text in
 * the user's own OpenRouter requests, and only while the setting is enabled.
 */
class LocationProvider(
    private val context: Context,
    private val preferences: AppPreferences
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    /** Refreshes the cached coarse fix. Returns true when a fresh location was stored. */
    @SuppressLint("MissingPermission") // guarded by hasPermission()
    suspend fun refresh(): Boolean {
        if (!hasPermission()) return false
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
            fused.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { /* lastLocation has no cancellation token */ }
        } ?: return false
        val label = withContext(Dispatchers.IO) { reverseGeocode(location.latitude, location.longitude) }
            ?: "%.3f, %.3f".format(Locale.US, location.latitude, location.longitude)
        preferences.setCachedLocation(label, location.latitude, location.longitude)
        return true
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String? = runCatching {
        @Suppress("DEPRECATION") // the async Geocoder API needs API 33+; minSdk is 28
        val address = Geocoder(context, Locale.getDefault())
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull() ?: return null
        listOfNotNull(address.locality ?: address.subAdminArea, address.countryName)
            .joinToString(", ")
            .ifBlank { null }
    }.getOrNull()

    /** System-prompt fragment describing the user's region, or blank when unavailable. */
    suspend fun promptContext(): String {
        val state = preferences.state.first()
        if (!state.locationEnabled || !hasPermission() || state.locationLabel.isBlank()) return ""
        return "The user's approximate location is ${state.locationLabel} " +
            "(lat ${"%.2f".format(Locale.US, state.locationLat)}, lon ${"%.2f".format(Locale.US, state.locationLon)}). " +
            "Use it when the request depends on their region, such as local places, weather, news, or services nearby."
    }
}
