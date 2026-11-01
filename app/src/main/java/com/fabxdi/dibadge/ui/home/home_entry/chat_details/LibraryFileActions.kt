package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.LibraryCategoryItem
import com.fabxdi.dibadge.viewmodel.LibraryFileItem

/**
 * Shared file actions for the Library root and the category screen.
 * Both screens call rename / move / delete / newRevision on this holder and draw
 * [LibraryFileDialogs] once, so the two screens can never drift apart again.
 */
@Stable
class LibraryFileActions {
    var toRename by mutableStateOf<LibraryFileItem?>(null)
    var toMove by mutableStateOf<LibraryFileItem?>(null)
    var toDelete by mutableStateOf<LibraryFileItem?>(null)
    var forRevision by mutableStateOf<LibraryFileItem?>(null)

    internal var launchRevisionPicker: () -> Unit = {}

    fun rename(file: LibraryFileItem) { toRename = file }
    fun move(file: LibraryFileItem) { toMove = file }
    fun delete(file: LibraryFileItem) { toDelete = file }
    fun newRevision(file: LibraryFileItem) {
        forRevision = file
        launchRevisionPicker()
    }
}

/** "Rev A" -> "Rev B", "B" -> "C", "Rev 2" -> "Rev 3". No revision yet means the first new one is "Rev B". */
fun nextRevisionLabel(current: String?): String {
    val cur = current?.trim().orEmpty()
    if (cur.isEmpty()) return "Rev B"
    Regex("^(.*\\s)?([A-Y])$").find(cur)?.let { m ->
        val prefix = m.groupValues[1]
        val letter = m.groupValues[2][0]
        return prefix + (letter + 1)
    }
    Regex("^(.*?)(\\d+)$").find(cur)?.let { m ->
        return m.groupValues[1] + (m.groupValues[2].toInt() + 1)
    }
    return "Rev B"
}

@Composable
fun rememberLibraryFileActions(
    groupId: String,
    detailsViewModel: GroupDetailsViewModel
): LibraryFileActions {
    val context = LocalContext.current
    val actions = remember { LibraryFileActions() }

    val revisionPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            val target = actions.forRevision
            if (uri != null && target != null) {
                detailsViewModel.uploadNewRevisionToLibrary(
                    groupId = groupId,
                    fileItem = target,
                    fileUri = uri,
                    newRevision = nextRevisionLabel(target.revision),
                    notifyMembers = true,
                    context = context,
                    onComplete = { actions.forRevision = null }
                )
            } else {
                actions.forRevision = null
            }
        }
    )
    SideEffect { actions.launchRevisionPicker = { revisionPicker.launch("*/*") } }
    return actions
}

@Composable
fun LibraryFileDialogs(
    actions: LibraryFileActions,
    groupId: String,
    allCategories: List<LibraryCategoryItem>,
    detailsViewModel: GroupDetailsViewModel
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error
    val dimTextColor = if (diBadgeColors.isDark) androidx.compose.ui.graphics.Color(0xFF6E757C) else androidx.compose.ui.graphics.Color(0xFF8A929A)

    // Rename
    actions.toRename?.let { target ->
        var input by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { actions.toRename = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Rename document", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = primaryTextColor),
                        cursorBrush = SolidColor(primaryTextColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(thickness = 1.dp, color = outlineColor, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        detailsViewModel.renameLibraryFile(groupId, target.id, input.trim())
                        Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                        actions.toRename = null
                    }
                ) {
                    Text("Save", color = if (input.isNotBlank()) tealAccent else dimTextColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.toRename = null }) { Text("Cancel", color = secondaryTextColor) }
            }
        )
    }

    // Move (includes "Files (no category)" so a file can come back out of a category)
    actions.toMove?.let { target ->
        val options = remember(target.id, target.categoryId, allCategories) {
            buildList {
                if (!target.categoryId.isNullOrBlank()) add("" to "Files (no category)")
                allCategories.filter { it.id != target.categoryId }.forEach { add(it.id to it.name) }
            }
        }
        var selectedId by remember(target.id) { mutableStateOf(options.firstOrNull()?.first) }

        AlertDialog(
            onDismissRequest = { actions.toMove = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Move to", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                if (options.isEmpty()) {
                    Text("There are no other places to move this to.", fontSize = 14.sp, color = secondaryTextColor)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.forEach { (id, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedId = id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedId == id,
                                    onClick = { selectedId = id },
                                    colors = RadioButtonDefaults.colors(selectedColor = tealAccent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = name, fontSize = 15.sp, color = primaryTextColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val chosen = options.firstOrNull { it.first == selectedId }
                TextButton(
                    enabled = chosen != null,
                    onClick = {
                        if (chosen != null) {
                            // An empty id and name means "no category"; the Library lists those files under Files.
                            detailsViewModel.moveLibraryFile(groupId, target.id, chosen.first, if (chosen.first.isEmpty()) "" else chosen.second)
                            Toast.makeText(context, "Moved to ${chosen.second}", Toast.LENGTH_SHORT).show()
                            actions.toMove = null
                        }
                    }
                ) {
                    Text("Move", color = if (chosen != null) tealAccent else dimTextColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.toMove = null }) { Text("Cancel", color = secondaryTextColor) }
            }
        )
    }

    // Delete
    actions.toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { actions.toDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Delete document?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = { Text("Delete ${target.name} for everyone in this group?", fontSize = 14.sp, color = secondaryTextColor) },
            confirmButton = {
                TextButton(
                    onClick = {
                        detailsViewModel.deleteLibraryFile(groupId, target.id)
                        Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                        actions.toDelete = null
                    }
                ) {
                    Text("Delete", color = errorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.toDelete = null }) { Text("Cancel", color = secondaryTextColor) }
            }
        )
    }
}
