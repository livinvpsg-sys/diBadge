package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    val retry: () -> Unit,
    /** True when the phone has no working internet. GPS still works; place names and maps do not. */
    val offline: Boolean = false
)

object DeviceLocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** True when the phone has a working internet connection right now. */
    fun isOnline(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        true
    }

    /**
     * Returns null when permission is missing, location is off, or no fix arrives in time.
     * GPS needs no internet. Without internet the first fix can take a minute or more (the phone
     * has no helper data), so the GPS wait is longer then and the place name is just the
     * coordinates.
     */
    @SuppressLint("MissingPermission")
    suspend fun fetch(context: Context, timeoutMs: Long = 10_000L): DeviceLocation? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        val online = isOnline(context)
        var fix: Location? = null
        for (provider in providers) {
            val wait = when {
                provider == LocationManager.GPS_PROVIDER && !online -> 60_000L
                provider == LocationManager.NETWORK_PROVIDER && !online -> 3_000L
                else -> timeoutMs
            }
            fix = withTimeoutOrNull(wait) { currentFix(context, lm, provider) }
            if (fix != null) break
        }
        if (fix == null) {
            fix = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }
        val loc = fix ?: return null
        // Place names come from an online lookup; offline (or if it is slow) show the coordinates
        val coords = String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
        val place = if (online) {
            withTimeoutOrNull(4_000L) { lookupPlaceName(context, loc.latitude, loc.longitude) } ?: coords
        } else {
            coords
        }
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
    var offline by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var askedBefore by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.any { it }
        askedBefore = true
        if (!granted) status = LocationStatus.NeedsPermission
    }

    LaunchedEffect(enabled, granted, attempt) {
        if (!enabled) return@LaunchedEffect
        if (!granted) {
            status = LocationStatus.NeedsPermission
            return@LaunchedEffect
        }
        status = LocationStatus.Loading
        offline = !DeviceLocationProvider.isOnline(context)
        val fix = DeviceLocationProvider.fetch(context)
        location = fix
        status = if (fix != null) LocationStatus.Ready else LocationStatus.Unavailable
    }

    return LocationState(
        status = status,
        location = location,
        requestPermission = {
            if (askedBefore) {
                // Android will not show the question again after a refusal, so send the person to Settings
                Toast.makeText(context, "Turn on Location for this app in Settings", Toast.LENGTH_LONG).show()
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        },
        retry = { attempt++ },
        offline = offline
    )
}
