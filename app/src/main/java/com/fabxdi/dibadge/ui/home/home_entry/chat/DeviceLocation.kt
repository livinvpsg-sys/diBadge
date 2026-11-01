package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** A one-shot fix of where the device is right now. */
data class DeviceLocation(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Int,
    val placeName: String
)

enum class LocationStatus { NeedsPermission, Loading, Ready, Unavailable }

class LocationState(
    val status: LocationStatus,
    val location: DeviceLocation?,
    val requestPermission: () -> Unit,
    val retry: () -> Unit
)

object DeviceLocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns null when permission is missing, location is off, or no fix arrives in time. */
    @SuppressLint("MissingPermission")
    suspend fun fetch(context: Context, timeoutMs: Long = 10_000L): DeviceLocation? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        var fix: Location? = null
        for (provider in providers) {
            fix = withTimeoutOrNull(timeoutMs) { currentFix(context, lm, provider) }
            if (fix != null) break
        }
        if (fix == null) {
            fix = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }
        val loc = fix ?: return null
        val place = lookupPlaceName(context, loc.latitude, loc.longitude)
        return DeviceLocation(
            lat = loc.latitude,
            lng = loc.longitude,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy.toInt().coerceAtLeast(1) else 0,
            placeName = place
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(context: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            val executor = Executor { r -> r.run() }
            cont.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, executor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun lookupPlaceName(context: Context, lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) {
            val fallback = String.format(Locale.US, "%.5f, %.5f", lat, lng)
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching fallback
                val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
                    ?: return@runCatching fallback
                val area = a.subLocality ?: a.locality ?: a.subAdminArea ?: a.adminArea
                val country = a.countryName
                when {
                    area != null && country != null -> "$area, $country"
                    area != null -> area
                    country != null -> country
                    else -> fallback
                }
            }.getOrDefault(fallback)
        }
}

/**
 * Asks for location permission when needed and keeps one fresh fix. Pass [enabled] = false to
 * stay idle (for example while a sheet is hidden).
 */
@Composable
fun rememberLocationState(enabled: Boolean = true): LocationState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(DeviceLocationProvider.hasPermission(context)) }
    var status by remember {
        mutableStateOf(if (granted) LocationStatus.Loading else LocationStatus.NeedsPermission)
    }
    var location by remember { mutableStateOf<DeviceLocation?>(null) }
    var attempt by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.any { it }
        if (!granted) status = LocationStatus.NeedsPermission
    }

    LaunchedEffect(enabled, granted, attempt) {
        if (!enabled) return@LaunchedEffect
        if (!granted) {
            status = LocationStatus.NeedsPermission
            return@LaunchedEffect
        }
        status = LocationStatus.Loading
        val fix = DeviceLocationProvider.fetch(context)
        location = fix
        status = if (fix != null) LocationStatus.Ready else LocationStatus.Unavailable
    }

    return LocationState(
        status = status,
        location = location,
        requestPermission = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        },
        retry = { attempt++ }
    )
}
