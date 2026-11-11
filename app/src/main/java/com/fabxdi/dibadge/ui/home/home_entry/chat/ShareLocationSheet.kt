package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private data class PlaceItem(
    val name: String,
    val detail: String,
    val lat: Double,
    val lng: Double,
    val distanceM: Int? = null,
    val kind: String = ""
)

/** Free OpenStreetMap services: no key needed. Please keep requests light (see their usage policy). */
private object PlacesApi {
    private const val UA = "diBadge/1.0 (Android chat app)"

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }

    fun distance(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Int {
        val r = 6371000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val x = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2) * sin(dLng / 2)
        return (r * 2 * atan2(sqrt(x), sqrt(1 - x))).roundToInt()
    }

    suspend fun search(query: String, nearLat: Double?, nearLng: Double?): List<PlaceItem> =
        withContext(Dispatchers.IO) {
            var url = "https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(query, "UTF-8")}" +
                    "&format=jsonv2&limit=8"
            if (nearLat != null && nearLng != null) {
                val d = 0.3
                url += "&viewbox=${nearLng - d},${nearLat + d},${nearLng + d},${nearLat - d}"
            }
            val body = get(url) ?: return@withContext emptyList()
            try {
                val arr = JSONArray(body)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val lat = o.getString("lat").toDouble()
                    val lng = o.getString("lon").toDouble()
                    val display = o.optString("display_name")
                    PlaceItem(
                        name = o.optString("name").ifBlank { display.substringBefore(",") },
                        detail = display.substringAfter(", ", ""),
                        lat = lat,
                        lng = lng,
                        distanceM = if (nearLat != null && nearLng != null) distance(nearLat, nearLng, lat, lng) else null
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun reverse(lat: Double, lng: Double): Pair<String, String>? = withContext(Dispatchers.IO) {
        val body = get("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=jsonv2&zoom=18")
            ?: return@withContext null
        try {
            val o = JSONObject(body)
            val display = o.optString("display_name")
            if (display.isBlank()) return@withContext null
            val name = o.optString("name").ifBlank { display.substringBefore(",") }
            name to display.substringAfter(", ", "")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun nearby(lat: Double, lng: Double): List<PlaceItem> = withContext(Dispatchers.IO) {
        val q = "[out:json][timeout:10];(" +
                "node(around:500,$lat,$lng)[\"name\"][\"amenity\"];" +
                "node(around:500,$lat,$lng)[\"name\"][\"shop\"];" +
                "node(around:500,$lat,$lng)[\"name\"][\"railway\"=\"station\"];" +
                "node(around:500,$lat,$lng)[\"name\"][\"tourism\"];" +
                "node(around:500,$lat,$lng)[\"name\"][\"leisure\"];" +
                ");out 40;"
        val body = get("https://overpass-api.de/api/interpreter?data=${URLEncoder.encode(q, "UTF-8")}")
            ?: return@withContext emptyList()
        try {
            val els = JSONObject(body).getJSONArray("elements")
            (0 until els.length()).mapNotNull { i ->
                val o = els.getJSONObject(i)
                val tags = o.optJSONObject("tags") ?: return@mapNotNull null
                val name = tags.optString("name")
                if (name.isBlank()) return@mapNotNull null
                val pLat = o.getDouble("lat")
                val pLng = o.getDouble("lon")
                val kind = when {
                    tags.optString("railway") == "station" -> "train"
                    tags.optString("amenity") in listOf("restaurant", "cafe", "fast_food", "food_court", "bar") -> "food"
                    tags.has("shop") -> "shop"
                    else -> "place"
                }
                val street = listOf(tags.optString("addr:street"), tags.optString("addr:housenumber"))
                    .filter { it.isNotBlank() }.joinToString(" ")
                PlaceItem(name, street, pLat, pLng, distance(lat, lng, pLat, pLng), kind)
            }.sortedBy { it.distanceM }.distinctBy { it.name }.take(8)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private fun distanceLabel(m: Int?): String = when {
    m == null -> ""
    m < 1000 -> "$m m"
    else -> String.format(java.util.Locale.US, "%.1f km", m / 1000.0)
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "train" -> Icons.Default.Train
    "food" -> Icons.Default.Restaurant
    "shop" -> Icons.Default.Store
    else -> Icons.Default.Place
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun ShareLocationSheet(
    isDark: Boolean,
    onSendCurrentLocation: (lat: Double, lng: Double, place: String, accuracy: Int) -> Unit,
    onSendLiveLocation: (lat: Double, lng: Double, place: String, durationMinutes: Int, accuracy: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val tealAccent = if (isDark) Color(0xFF8F9CD8) else Color(0xFF3A4694)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    // Device position (asks for permission when needed)
    val locationState = rememberLocationState()
    val loc = locationState.location
    val isReady = locationState.status == LocationStatus.Ready && loc != null
    val statusLine = when (locationState.status) {
        LocationStatus.Ready -> "${loc?.placeName.orEmpty()} · accurate to ${loc?.accuracyMeters ?: 0} m"
        LocationStatus.Loading -> if (locationState.offline) {
            "No internet"
        } else {
            "Finding your location…"
        }
        LocationStatus.NeedsPermission -> "Allow location access to share where you are"
        LocationStatus.Unavailable -> if (locationState.offline) {
            "Turn on location"
        } else {
            "Couldn't get your location. Turn on location and tap to try again"
        }
    }
    val onRowTap: () -> Unit = {
        when (locationState.status) {
            LocationStatus.NeedsPermission -> locationState.requestPermission()
            LocationStatus.Unavailable -> locationState.retry()
            else -> Unit
        }
    }

    // 0 = no time limit
    val durations = listOf(15 to "15 min", 60 to "1 hour", 480 to "8 hours", 0 to "No limit")
    var selectedDuration by remember { mutableStateOf(60) }
    val durationText = if (selectedDuration == 0) "Shares until you stop it" else
        "Shares for ${durations.first { it.first == selectedDuration }.second.lowercase()}"

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var nearby by remember { mutableStateOf<List<PlaceItem>>(emptyList()) }
    var selected by remember { mutableStateOf<PlaceItem?>(null) }   // a place or a pinned spot to send
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var userMovedMap by remember { mutableStateOf(false) }
    var pinCenter by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(loc?.lat, loc?.lng) {
        val l = loc ?: return@LaunchedEffect
        nearby = PlacesApi.nearby(l.lat, l.lng)
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(600)
        searching = true
        results = PlacesApi.search(query.trim(), loc?.lat, loc?.lng)
        searching = false
    }

    // Centre the map on the device the first time a fix arrives
    LaunchedEffect(loc?.lat, loc?.lng, mapView) {
        val l = loc ?: return@LaunchedEffect
        if (selected == null) mapView?.controller?.setCenter(GeoPoint(l.lat, l.lng))
    }

    // Pin mode: after the person drags the map, look up the address under the pin
    LaunchedEffect(pinCenter) {
        val c = pinCenter ?: return@LaunchedEffect
        if (selected == null || !userMovedMap) return@LaunchedEffect
        delay(700)
        val addr = PlacesApi.reverse(c.latitude, c.longitude)
        selected = PlaceItem(
            name = addr?.first ?: String.format(java.util.Locale.US, "%.5f, %.5f", c.latitude, c.longitude),
            detail = addr?.second.orEmpty(),
            lat = c.latitude,
            lng = c.longitude
        )
    }

    fun pickPlace(p: PlaceItem) {
        userMovedMap = false
        selected = p
        mapView?.controller?.setZoom(17.0)
        mapView?.controller?.animateTo(GeoPoint(p.lat, p.lng))
    }

    DisposableEffect(Unit) {
        onDispose { mapView?.onDetach() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        Surface(modifier = Modifier.fillMaxSize(), color = cardColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { if (selected != null) selected = null else onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = primaryTextColor)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selected != null) "Send this location" else "Share location",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                }

                // Search
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(42.dp),
                    shape = RoundedCornerShape(21.dp),
                    color = cardFillColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text("Search places", fontSize = 15.sp, color = secondaryTextColor)
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, color = primaryTextColor),
                                cursorBrush = SolidColor(tealAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = secondaryTextColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { query = "" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Map (OpenStreetMap, no key)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(if (selected != null) 300.dp else 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardFillColor)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            Configuration.getInstance().apply {
                                load(ctx, ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
                                userAgentValue = ctx.packageName
                                osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
                                osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
                            }
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                controller.setZoom(16.5)
                                controller.setCenter(GeoPoint(1.3521, 103.8198))
                                if (isDark) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                                addMapListener(object : MapListener {
                                    override fun onScroll(event: ScrollEvent?): Boolean {
                                        val c = mapCenter
                                        pinCenter = GeoPoint(c.latitude, c.longitude)
                                        return true
                                    }

                                    override fun onZoom(event: ZoomEvent?): Boolean = false
                                })
                                setOnTouchListener { v, ev ->
                                    if (ev.action == MotionEvent.ACTION_DOWN) {
                                        userMovedMap = true
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    false
                                }
                                mapView = this
                            }
                        },
                        update = { map ->
                            map.overlays.removeAll { it is Marker }
                            val l = loc
                            if (l != null && selected == null) {
                                val m = Marker(map).apply {
                                    position = GeoPoint(l.lat, l.lng)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "You are here"
                                }
                                map.overlays.add(m)
                            }
                            map.invalidate()
                        }
                    )

                    // Centre pin while choosing a spot
                    if (selected != null) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Pin",
                            tint = errorColor,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = (-20).dp)
                                .size(40.dp)
                        )
                    }

                    // Back to my position
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(38.dp)
                            .clickable {
                                val l = loc
                                if (l != null) {
                                    userMovedMap = false
                                    mapView?.controller?.animateTo(GeoPoint(l.lat, l.lng))
                                } else onRowTap()
                            },
                        shape = CircleShape,
                        color = cardColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MyLocation, "My location", tint = tealAccent, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Pin mode: show what will be sent
                val sel = selected
                if (sel != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LocationRowIcon(Icons.Default.Place, errorColor, cardFillColor, outlineColor)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sel.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = sel.detail.ifBlank { String.format(java.util.Locale.US, "%.5f, %.5f", sel.lat, sel.lng) },
                                fontSize = 12.sp, color = secondaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Drag the map to move the pin",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(start = 70.dp, top = 6.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(48.dp)
                            .clickable {
                                onSendCurrentLocation(sel.lat, sel.lng, sel.name, 0)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = tealAccent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Send this location", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (query.trim().length >= 2) {
                            item {
                                SectionLabel(if (searching) "SEARCHING…" else "RESULTS", secondaryTextColor)
                            }
                            if (searching) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = tealAccent)
                                    }
                                }
                            } else if (results.isEmpty()) {
                                item {
                                    Text("No places found", fontSize = 14.sp, color = secondaryTextColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                                }
                            }
                            items(results) { p ->
                                PlaceRow(p, primaryTextColor, secondaryTextColor, cardFillColor, outlineColor) { pickPlace(p) }
                            }
                        } else {
                            // Current location
                            item {
                                ActionRow(
                                    icon = Icons.Default.MyLocation,
                                    iconTint = primaryTextColor,
                                    title = "Send current location",
                                    subtitle = statusLine,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    cardFillColor = cardFillColor,
                                    outlineColor = outlineColor
                                ) {
                                    val l = loc
                                    if (isReady && l != null) {
                                        onSendCurrentLocation(l.lat, l.lng, l.placeName, l.accuracyMeters)
                                        onDismiss()
                                    } else onRowTap()
                                }
                            }
                            // Live location with the time choice
                            item {
                                ActionRow(
                                    icon = Icons.Outlined.NearMe,
                                    iconTint = primaryTextColor,
                                    title = "Share live location",
                                    subtitle = durationText,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    cardFillColor = cardFillColor,
                                    outlineColor = outlineColor
                                ) {
                                    val l = loc
                                    if (isReady && l != null) {
                                        onSendLiveLocation(l.lat, l.lng, l.placeName, selectedDuration, l.accuracyMeters)
                                        onDismiss()
                                    } else onRowTap()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 70.dp, end = 16.dp, bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    durations.forEach { (mins, label) ->
                                        val on = selectedDuration == mins
                                        Surface(
                                            modifier = Modifier.clickable { selectedDuration = mins },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (on) tealAccent.copy(alpha = 0.14f) else Color.Transparent,
                                            border = BorderStroke(1.dp, if (on) tealAccent else outlineColor)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                color = if (on) tealAccent else secondaryTextColor,
                                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            // Pick a spot
                            item {
                                ActionRow(
                                    icon = Icons.Default.Place,
                                    iconTint = primaryTextColor,
                                    title = "Pick a spot on the map",
                                    subtitle = "Move the pin, then send",
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    cardFillColor = cardFillColor,
                                    outlineColor = outlineColor
                                ) {
                                    val c = mapView?.mapCenter
                                    val lat = c?.latitude ?: loc?.lat ?: 1.3521
                                    val lng = c?.longitude ?: loc?.lng ?: 103.8198
                                    userMovedMap = true
                                    pinCenter = GeoPoint(lat, lng)
                                    selected = PlaceItem(
                                        name = String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng),
                                        detail = "",
                                        lat = lat,
                                        lng = lng
                                    )
                                }
                            }
                            if (nearby.isNotEmpty()) {
                                item { SectionLabel("NEARBY PLACES", secondaryTextColor) }
                                items(nearby) { p ->
                                    PlaceRow(p, primaryTextColor, secondaryTextColor, cardFillColor, outlineColor) { pickPlace(p) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun LocationRowIcon(icon: ImageVector, tint: Color, fill: Color, outline: Color) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(1.dp, outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardFillColor: Color,
    outlineColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocationRowIcon(icon, iconTint, cardFillColor, outlineColor)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = secondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaceRow(
    place: PlaceItem,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardFillColor: Color,
    outlineColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocationRowIcon(kindIcon(place.kind), primaryTextColor, cardFillColor, outlineColor)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(place.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(place.detail, distanceLabel(place.distanceM)).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 12.sp, color = secondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}