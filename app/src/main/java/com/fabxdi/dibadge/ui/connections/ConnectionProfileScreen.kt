package com.fabxdi.dibadge.ui.connections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.CountryData
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.util.capitalizeName
import com.fabxdi.dibadge.viewmodel.ConnectionItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One job from the person's Experience list (users/{uid}/experience). */
private data class ProfileJob(
    val role: String,
    val company: String,
    val start: LocalDate?,
    val end: LocalDate?,
    val current: Boolean
)

private fun parseDay(s: String?): LocalDate? = runCatching { LocalDate.parse(s ?: "") }.getOrNull()

private fun jobRange(job: ProfileJob): String {
    val fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
    val from = job.start?.format(fmt).orEmpty()
    val to = if (job.current) "Present" else job.end?.format(fmt).orEmpty()
    return listOf(from, to).filter { it.isNotBlank() }.joinToString(" - ")
}

/**
 * The full profile of a connection or request, opened by tapping the person.
 * The top quarter is their photo (or coloured avatar), then name, role, the date, the other details and their
 * experience. The actions are pinned at the bottom: Chat / Call / Video (connected), Decline / Accept (request
 * received), Pending / Cancel request (request sent), Connect (anyone else). Disconnect is in the top-right menu.
 */
@Composable
fun ConnectionProfileScreen(
    item: ConnectionItem,
    onBack: () -> Unit,
    onChat: () -> Unit,
    onCall: (video: Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onCancel: () -> Unit = {},
    onConnect: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val accent = DiBadgeTheme.colors.accent
    val pageBg = colors.background
    val primary = colors.onBackground
    val secondary = colors.onSurfaceVariant
    val name = capitalizeName(item.name)
    val connected = item.status == "connected"
    val received = item.status == "received"
    val sent = item.status == "sent"
    var menuOpen by remember { mutableStateOf(false) }
    val avatarColor = remember(item.id, item.uid) { UserColorUtils.getColorForName(item.uid.ifBlank { item.id }) }
    val country = remember(item.country) { if (item.country.isBlank()) "" else CountryData.getCountryDisplayName(item.country) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val context = androidx.compose.ui.platform.LocalContext.current

    // The experience list is read once when the screen opens. If the rules do not allow it, the section stays hidden.
    var jobs by remember(item.uid) { mutableStateOf<List<ProfileJob>>(emptyList()) }
    LaunchedEffect(item.uid) {
        if (item.uid.isBlank()) return@LaunchedEffect
        jobs = runCatching {
            FirebaseFirestore.getInstance().collection("users").document(item.uid)
                .collection("experience").get().await().documents.map { d ->
                    ProfileJob(
                        role = d.getString("role").orEmpty(),
                        company = d.getString("company").orEmpty(),
                        start = parseDay(d.getString("startDate")),
                        end = parseDay(d.getString("endDate")),
                        current = d.getBoolean("isCurrent") == true
                    )
                }.filter { it.role.isNotBlank() || it.company.isNotBlank() }
                .sortedWith(compareByDescending<ProfileJob> { it.current }.thenByDescending { it.start ?: LocalDate.MIN })
        }.getOrDefault(emptyList())
    }

    val headline = when {
        item.role.isNotBlank() && item.organization.isNotBlank() -> "${item.role} at ${item.organization}"
        item.role.isNotBlank() -> item.role
        else -> item.organization
    }

    // A normal full-screen layer (not a Dialog), so the phone's own status and navigation bar spacing applies
    BackHandler(onBack = onBack)
    run {
        Surface(modifier = Modifier.fillMaxSize(), color = pageBg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Top quarter: photo, or the coloured avatar with their initial
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenHeight * 0.25f)
                            .background(
                                Brush.verticalGradient(listOf(avatarColor, lerp(avatarColor, Color.Black, 0.28f)))
                            )
                    ) {
                        if (item.photoUrl.isNotBlank()) {
                            // Behind: a blurred, darkened copy of the photo (the blur needs Android 12; older phones get the colour)
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                AsyncImage(
                                    model = item.photoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().blur(28.dp)
                                )
                            }
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                            // In front: the photo as a circle, the same shape as everywhere else in the app
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = item.photoUrl,
                                    contentDescription = "Photo of $name",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                                )
                            }
                        } else {
                            Text(
                                text = name.take(1),
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.95f),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        // A soft dark fade at the top keeps the back arrow easy to see on any photo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                        )
                        Box(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(10.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        // Connected people only: a small menu with Disconnect
                        if (connected) {
                            Box(modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .clickable { menuOpen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.MoreVert, "More", tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Disconnect from $name", color = colors.error) },
                                        leadingIcon = { Icon(Icons.Outlined.PersonRemove, null, tint = colors.error) },
                                        onClick = {
                                            menuOpen = false
                                            onDisconnect()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(text = name, fontSize = 22.sp, color = primary)
                        if (headline.isNotBlank()) {
                            Text(text = headline, fontSize = 14.sp, color = secondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        val dateMillis = if (connected) item.connectedAt else if (received || sent) item.timestamp else 0L
                        if (dateMillis > 0L) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Outlined.CalendarToday, null, tint = secondary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = (if (connected) "Connected " else "Requested ") + remember(dateMillis) {
                                        java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                            .format(java.util.Date(dateMillis))
                                    },
                                    fontSize = 13.sp,
                                    color = secondary
                                )
                            }
                        }

                        // Actions sit right under the name, role and date
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when {
                                connected -> {
                                    ProfileBarPill("Chat", accent, Modifier.weight(1f), true, onChat) {
                                        ChatFilledIcon(tint = accent, contentDescription = null, size = 18)
                                    }
                                    ProfileBarPill("Call", accent, Modifier.weight(1f), true, { onCall(false) }) {
                                        Icon(Icons.Outlined.Call, null, tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                    ProfileBarPill("Video", accent, Modifier.weight(1f), true, { onCall(true) }) {
                                        Icon(Icons.Outlined.Videocam, null, tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                                received -> {
                                    ProfileBarPill("Decline", secondary, Modifier.weight(1f), true, onDecline) {
                                        Icon(Icons.Outlined.Close, null, tint = secondary, modifier = Modifier.size(18.dp))
                                    }
                                    ProfileBarPill("Accept", accent, Modifier.weight(1f), true, onAccept) {
                                        Icon(Icons.Outlined.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                                sent -> {
                                    ProfileBarPill("Pending", secondary, Modifier.weight(1f), false, {}) {
                                        Icon(Icons.Outlined.Schedule, null, tint = secondary, modifier = Modifier.size(18.dp))
                                    }
                                    ProfileBarPill("Cancel request", accent, Modifier.weight(1f), true, onCancel) {
                                        Icon(Icons.Outlined.Close, null, tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                                else -> {
                                    ProfileBarPill("Connect", accent, Modifier.width(160.dp), true, onConnect) {
                                        Icon(Icons.Outlined.PersonAdd, null, tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        if (item.bio.isNotBlank()) ProfileDetailRow(Icons.Outlined.Info, "About", item.bio, primary, secondary)
                        if (country.isNotBlank()) ProfileDetailRow(Icons.Outlined.LocationOn, "Location", country, primary, secondary)
                        if (connected && item.badgeNumber.isNotBlank()) {
                            ProfileDetailRow(
                                Icons.Outlined.Badge, "Badge number", item.badgeNumber, primary, secondary,
                                letterSpacing = 1.sp,
                                onClick = {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Badge number", item.badgeNumber))
                                    android.widget.Toast.makeText(context, "Badge number copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (jobs.isNotEmpty()) {
                            Text(
                                text = "EXPERIENCE",
                                fontSize = 12.sp,
                                letterSpacing = 0.6.sp,
                                color = secondary,
                                modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                            )
                            jobs.forEachIndexed { index, job ->
                                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(modifier = Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(accent))
                                        if (index < jobs.lastIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 3.dp)
                                                    .width(1.dp)
                                                    .fillMaxHeight()
                                                    .background(secondary.copy(alpha = 0.35f))
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.padding(bottom = if (index < jobs.lastIndex) 14.dp else 0.dp)) {
                                        if (job.role.isNotBlank()) Text(job.role, fontSize = 15.sp, color = primary)
                                        if (job.company.isNotBlank()) Text(job.company, fontSize = 13.sp, color = secondary)
                                        val range = jobRange(job)
                                        if (range.isNotBlank()) Text(range, fontSize = 12.sp, color = secondary.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.navigationBarsPadding().height(16.dp))
                    }
                }

            }
        }
    }
}

/** Outlined action pill shown under the date on the profile screen. */
@Composable
private fun ProfileBarPill(
    label: String,
    color: Color,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(21.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(21.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.5f else 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(text = label, fontSize = 14.sp, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun ProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    primary: Color,
    secondary: Color,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, fontSize = 11.sp, color = secondary)
            Text(value, fontSize = 15.sp, color = primary, letterSpacing = letterSpacing)
        }
    }
}