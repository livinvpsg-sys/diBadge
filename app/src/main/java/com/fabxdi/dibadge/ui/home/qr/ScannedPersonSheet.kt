package com.fabxdi.dibadge.ui.home.qr

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.ui.connections.ConnectionProfileScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.CountryData
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.util.capitalizeName
import com.fabxdi.dibadge.viewmodel.ConnectionItem
import com.fabxdi.dibadge.viewmodel.ConnectionsViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Who a QR code or invite link belongs to, with "Send connection request".
 * [code] is the person's QR code; [ownCode] is mine (so my own code is not offered).
 * [onClose] closes the sheet; [onRequestSent] is called with their name after a request was sent or accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannedPersonSheet(
    code: String,
    ownCode: String,
    onClose: () -> Unit,
    onRequestSent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val tealAccent = DiBadgeTheme.colors.accent

    val connectionsVm: ConnectionsViewModel = viewModel()
    val myConnections by connectionsVm.connections.collectAsState()
    val mySent by connectionsVm.sentRequests.collectAsState()
    val myReceived by connectionsVm.receivedRequests.collectAsState()
    var person by remember(code) { mutableStateOf<ConnectionItem?>(null) }
    var loading by remember(code) { mutableStateOf(true) }
    var showProfile by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        loading = true
        person = runCatching {
            val q = FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("qrCode", code.trim().uppercase()).limit(1).get().await()
            q.documents.firstOrNull()?.let { d ->
                ConnectionItem(
                    id = d.id,
                    uid = d.id,
                    name = d.getString("displayName") ?: d.getString("name") ?: "User",
                    organization = d.getString("organization") ?: d.getString("company") ?: "",
                    role = d.getString("role") ?: d.getString("jobTitle") ?: "",
                    bio = d.getString("bio") ?: "",
                    photoUrl = d.getString("photoUrl") ?: d.getString("photoURL") ?: "",
                    status = "none",
                    country = d.getString("country") ?: ""
                )
            }
        }.getOrNull()
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val p = person
            if (loading) {
                CircularProgressIndicator(color = tealAccent, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.height(24.dp))
            } else if (p == null) {
                Text("No diBadge user found for this code", fontSize = 16.sp, color = primaryTextColor, textAlign = TextAlign.Center)
                Text("The code may have been reset", fontSize = 13.sp, color = secondaryTextColor, modifier = Modifier.padding(top = 4.dp))
                Spacer(modifier = Modifier.height(16.dp))
            } else if (ownCode.isNotBlank() && code.equals(ownCode, ignoreCase = true)) {
                Text("This is your own code", fontSize = 16.sp, color = primaryTextColor)
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                val personName = capitalizeName(p.name)
                val avatarColor = remember(p.uid) { UserColorUtils.getColorForName(p.uid) }
                val headline = when {
                    p.role.isNotBlank() && p.organization.isNotBlank() -> "${p.role} at ${p.organization}"
                    p.role.isNotBlank() -> p.role
                    else -> p.organization
                }
                val country = remember(p.country) { if (p.country.isBlank()) "" else CountryData.getCountryDisplayName(p.country) }

                // Tap the photo for the full profile
                Surface(
                    modifier = Modifier.size(64.dp).clickable { showProfile = true },
                    shape = CircleShape,
                    color = avatarColor,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    if (p.photoUrl.isNotBlank()) {
                        AsyncImage(model = p.photoUrl, contentDescription = personName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(personName.take(1), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(personName, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                if (headline.isNotBlank()) Text(headline, fontSize = 14.sp, color = secondaryTextColor, modifier = Modifier.padding(top = 2.dp))
                if (country.isNotBlank()) Text(country, fontSize = 13.sp, color = secondaryTextColor, modifier = Modifier.padding(top = 2.dp))
                Text("Tap the photo for the full profile", fontSize = 12.sp, color = secondaryTextColor.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp))

                Spacer(modifier = Modifier.height(16.dp))

                val isConnected = myConnections.any { it.uid == p.uid }
                val isSent = mySent.any { it.uid == p.uid }
                val incoming = myReceived.firstOrNull { it.uid == p.uid }
                when {
                    isConnected -> Text("Already connected", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    isSent -> Text("Request already sent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    incoming != null -> Text(
                        text = "Accept request",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                connectionsVm.acceptRequest(incoming)
                                Toast.makeText(context, "Connected with $personName", Toast.LENGTH_SHORT).show()
                                onRequestSent(personName)
                                onClose()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    else -> Text(
                        text = "Send connection request",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                connectionsVm.sendInvite(p)
                                Toast.makeText(context, "Request sent to $personName", Toast.LENGTH_SHORT).show()
                                onRequestSent(personName)
                                onClose()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cancel",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTextColor,
                modifier = Modifier.clickable { onClose() }.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }

    // The full profile (no chat or call buttons: they are not connected yet)
    val shown = person
    if (showProfile && shown != null) {
        ConnectionProfileScreen(
            item = shown,
            onBack = { showProfile = false },
            onChat = {},
            onCall = {},
            onDisconnect = {}
        )
    }
}
