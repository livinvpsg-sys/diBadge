package com.fabxdi.dibadge.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

data class BlockedUserItem(
    val id: String,
    val name: String,
    val nickname: String = "",
    val organization: String = "",
    /** The whole stored record, so Undo can put it back exactly as it was. */
    val rawData: Map<String, Any?> = emptyMap()
)

@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit
) {
    // Read every time so a different signed-in account is never shown the previous account's list
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var blocked by remember(currentUid) { mutableStateOf(emptyList<BlockedUserItem>()) }
    var nicknames by remember(currentUid) { mutableStateOf(emptyMap<String, String>()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // One listener for the blocked list and one for all private nicknames, both removed on exit
    DisposableEffect(currentUid) {
        if (currentUid.isBlank()) return@DisposableEffect onDispose { }
        val db = FirebaseFirestore.getInstance()

        val blockedReg = db.collection("blockedUsers").document(currentUid)
            .collection("blocked")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                blocked = snapshot.documents.map { doc ->
                    BlockedUserItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "User",
                        organization = doc.getString("organization") ?: "",
                        rawData = doc.data ?: emptyMap()
                    )
                }
            }

        val nickReg = db.collection("users").document(currentUid)
            .collection("nicknames")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                nicknames = snapshot.documents.associate { it.id to (it.getString("nickname") ?: "") }
            }

        onDispose {
            blockedReg.remove()
            nickReg.remove()
        }
    }

    val blockedUsers = remember(blocked, nicknames) {
        blocked.map { it.copy(nickname = nicknames[it.id].orEmpty()) }
    }

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tabInactiveColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Blocked users",
                onBackClick = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            if (blockedUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = null,
                            tint = tabInactiveColor,
                            modifier = Modifier.size(44.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "No blocked users",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "People you block cannot find you or send you connection requests",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    items(blockedUsers, key = { it.id }) { user ->
                        val avatarColor = remember(user.id) { UserColorUtils.getColorForName(user.id) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = avatarColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user.name.take(1).uppercase(),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subText = if (user.nickname.isNotBlank()) user.nickname else user.organization
                                    if (subText.isNotBlank()) {
                                        Text(
                                            text = subText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = secondaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(role = Role.Button) {
                                        if (currentUid.isBlank()) return@clickable
                                        val db = FirebaseFirestore.getInstance()
                                        val ref = db.collection("blockedUsers").document(currentUid)
                                            .collection("blocked").document(user.id)
                                        ref.delete()
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Unblocked ${user.name}",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                ref.set(user.rawData.ifEmpty {
                                                    mapOf("name" to user.name, "organization" to user.organization)
                                                })
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Unblock",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tealAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
