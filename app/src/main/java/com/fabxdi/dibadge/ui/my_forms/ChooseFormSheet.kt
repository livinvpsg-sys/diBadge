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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseFormSheet(
    groupForms: List<GroupFormEntity>,
    canUserManage: Boolean,
    onSelectForm: (GroupFormEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive

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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Forms in this group",
                fontSize = 13.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (groupForms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (canUserManage) "Import your first form" else "Forms will appear here",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (canUserManage) "Tap Import form to bring in a form from My Forms" else "The owner or an admin adds the forms for this group",
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(groupForms, key = { it.id }) { gForm ->
                        val questions = remember(gForm.questionsJson) {
                            parseQuestionsFromJson(gForm.questionsJson)
                        }

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
