package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.ContactShare
import com.fabxdi.dibadge.util.SharedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pick one or more contacts from the phone to send in the chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerSheet(
    onDismiss: () -> Unit,
    onSend: (List<SharedContact>) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val accent = DiBadgeTheme.colors.accent
    val primary = colors.onBackground
    val secondary = colors.onSurfaceVariant
    val outline = colors.outline

    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        asked = true
    }
    LaunchedEffect(Unit) { if (!allowed) permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }

    var contacts by remember { mutableStateOf<List<SharedContact>?>(null) }
    LaunchedEffect(allowed) {
        if (allowed) contacts = withContext(Dispatchers.IO) { ContactShare.loadAll(context) }
    }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<SharedContact>()) }
    val palette = listOf(0xFFE39A6B, 0xFF6BA8E3, 0xFF9B7BD6, 0xFF5FB88C, 0xFFD66B8A, 0xFFC9A93F)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
            Text(
                text = if (picked.isEmpty()) "Share contacts" else "${picked.size} selected",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            if (!allowed) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (asked) "diBadge needs permission to read your contacts so you can pick who to share."
                        else "Allow contacts to share them in this chat.",
                        fontSize = 15.sp,
                        color = secondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Allow contacts", color = accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Search
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = secondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search contacts", fontSize = 15.sp, color = secondary)
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = primary),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val list = contacts
                if (list == null) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                } else {
                    val shown = remember(list, query) {
                        val q = query.trim()
                        if (q.isEmpty()) list
                        else list.filter { c -> c.name.contains(q, true) || c.phones.any { it.contains(q) } }
                    }
                    if (shown.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                if (list.isEmpty()) "No contacts found on this phone" else "No match",
                                fontSize = 15.sp, color = secondary
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(shown) { c ->
                                val isPicked = picked.any { it === c }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            picked = if (isPicked) picked.filter { it !== c } else picked + c
                                        }
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape)
                                            .background(Color(palette[(c.name.hashCode() and 0x7FFFFFFF) % palette.size])),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            c.name.trim().firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.name, fontSize = 16.sp, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            c.phones.firstOrNull() ?: c.emails.firstOrNull().orEmpty(),
                                            fontSize = 13.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.size(24.dp).clip(CircleShape)
                                            .background(if (isPicked) accent else Color.Transparent)
                                            .border(1.5.dp, if (isPicked) accent else outline, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPicked) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Send
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Button(
                        onClick = { onSend(picked) },
                        enabled = picked.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                    ) {
                        Text(
                            if (picked.isEmpty()) "Select contacts" else "Send ${picked.size} contact${if (picked.size == 1) "" else "s"}",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}