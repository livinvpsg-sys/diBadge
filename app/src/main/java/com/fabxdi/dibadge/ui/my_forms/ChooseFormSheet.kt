package com.fabxdi.dibadge.ui.my_forms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseFormSheet(
    groupForms: List<GroupFormEntity>,
    canUserManage: Boolean,
    isOrgGroup: Boolean = false,
    orgName: String = "",
    onSelectForm: (GroupFormEntity) -> Unit,
    onOpenMyForms: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Choose a form",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (groupForms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val emptyTitle = when {
                            isOrgGroup -> "Forms will appear here"
                            canUserManage -> "No forms in this group yet"
                            else -> "Forms will appear here"
                        }
                        val emptySubtitle = when {
                            isOrgGroup -> "${if (orgName.isNotBlank()) orgName else "The organisation"} adds the forms for this group"
                            canUserManage -> "Open My Forms from the side menu and choose Add to group"
                            else -> "The owner or an admin adds the forms for this group"
                        }

                        Text(
                            text = emptyTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = emptySubtitle,
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                        if (!isOrgGroup && canUserManage && onOpenMyForms != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Open My Forms",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = tealAccent,
                                modifier = Modifier
                                    .clickable {
                                        onDismiss()
                                        onOpenMyForms()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(groupForms, key = { it.id }) { gForm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable {
                                    onDismiss()
                                    onSelectForm(gForm)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 36dp Rounded Icon Tile
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, outlineColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_forms_outline),
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gForm.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val formattedDate = remember(gForm.addedAtMs) {
                                    val dt = Instant.ofEpochMilli(gForm.addedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
                                    dt.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                                }
                                val sourceStr = if (gForm.isOrgForm) "From ${gForm.orgName}" else "Updated $formattedDate"
                                Text(
                                    text = sourceStr,
                                    fontSize = 13.sp,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    }
                }
            }
        }
    }
}
