package com.fabxdi.dibadge.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.data.ClaimEntity
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRequestSheet(
    onSelectLeave: () -> Unit,
    onSelectOvertime: () -> Unit,
    onSelectClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "New request",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: Leave
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable {
                        onDismiss()
                        onSelectLeave()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_leave),
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Leave", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                    Text("Request days off", fontSize = 13.sp, color = secondaryTextColor)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Row 2: Overtime
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable {
                        onDismiss()
                        onSelectOvertime()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Extra hours", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                    Text("Log extra hours", fontSize = 13.sp, color = secondaryTextColor)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Row 3: Claim
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable {
                        onDismiss()
                        onSelectClaim()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Claim", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                    Text("Submit an expense", fontSize = 13.sp, color = secondaryTextColor)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPopupSheet(
    date: LocalDate,
    leaves: List<LeaveEntity>,
    overtimes: List<OvertimeEntity>,
    claims: List<ClaimEntity>,
    onLeaveClick: (LeaveEntity) -> Unit,
    onOvertimeClick: (OvertimeEntity) -> Unit,
    onClaimClick: (ClaimEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val tealAccent = diBadgeColors.accent
    val slatePendingColor = diBadgeColors.tabInactive
    val isDark = primaryTextColor.red > 0.5f
    val rowItemBgColor = if (isDark) Color(0xFF222831) else Color(0xFFE8ECEF)

    var expandedType by remember { mutableStateOf<String?>(null) } // "Leave", "Overtime", "Claim"

    val formattedDateTitle = remember(date) {
        date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
    }

    val hasAny = leaves.isNotEmpty() || overtimes.isNotEmpty() || claims.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .animateContentSize(animationSpec = tween(200))
        ) {
            Text(
                text = formattedDateTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasAny) {
                Text(
                    text = "Nothing on this day",
                    fontSize = 15.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                // 1. Leave Section
                if (leaves.isNotEmpty()) {
                    val hasApproved = leaves.any { it.status.equals("Approved", ignoreCase = true) }
                    val hasPending = leaves.any { it.status.equals("Pending", ignoreCase = true) }
                    val isExpanded = expandedType == "Leave"

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { expandedType = if (isExpanded) null else "Leave" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Leave", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                                if (!isExpanded) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (hasApproved) {
                                        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = tealAccent) {}
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (hasPending) {
                                        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = slatePendingColor) {}
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${leaves.size} ${if (leaves.size == 1) "day" else "days"}", fontSize = 14.sp, color = secondaryTextColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                if (!isExpanded) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(20.dp))
                                }
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            leaves.forEach { leave ->
                                val isApproved = leave.status.equals("Approved", ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onLeaveClick(leave)
                                        }
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = rowItemBgColor
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "${if (isApproved) "Approved" else "Pending"} · ${leave.leaveType}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isApproved) tealAccent else slatePendingColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 2. Extra hours Section
                if (overtimes.isNotEmpty()) {
                    val hasApproved = overtimes.any { it.status.equals("Approved", ignoreCase = true) }
                    val hasPending = overtimes.any { it.status.equals("Pending", ignoreCase = true) }
                    val isExpanded = expandedType == "Extra hours"
                    val totalHrs = overtimes.sumOf {
                        val duration = Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
                        if (duration < 0) duration + 24.0 else duration
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { expandedType = if (isExpanded) null else "Extra hours" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Extra hours", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                                if (!isExpanded) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (hasApproved) {
                                        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = tealAccent) {}
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (hasPending) {
                                        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = slatePendingColor) {}
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$totalHrs hrs", fontSize = 14.sp, color = secondaryTextColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                if (!isExpanded) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(20.dp))
                                }
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            overtimes.forEach { ot ->
                                val isApproved = ot.status.equals("Approved", ignoreCase = true)
                                val hrs = (Duration.between(ot.startTime, ot.endTime).toMinutes() / 60.0).let { if (it < 0) it + 24.0 else it }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onOvertimeClick(ot)
                                        }
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = rowItemBgColor
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "${if (isApproved) "Approved" else "Pending"} · $hrs hrs",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isApproved) tealAccent else slatePendingColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 3. Claim Section
                if (claims.isNotEmpty()) {
                    val hasApproved = claims.any { it.status.equals("Approved", ignoreCase = true) }
                    val hasPending = claims.any { it.status.equals("Pending", ignoreCase = true) }
                    val isExpanded = expandedType == "Claim"
                    val totalAmount = claims.sumOf { it.amount }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { expandedType = if (isExpanded) null else "Claim" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Claim", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                                if (!isExpanded) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (hasApproved) {
                                        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = tealAccent) {}
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (hasPending) {
                                        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = slatePendingColor) {}
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("S$${String.format(Locale.US, "%.2f", totalAmount)}", fontSize = 14.sp, color = secondaryTextColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                if (!isExpanded) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(20.dp))
                                }
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            claims.forEach { claim ->
                                val isApproved = claim.status.equals("Approved", ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onClaimClick(claim)
                                        }
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = rowItemBgColor
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "${if (isApproved) "Approved" else "Pending"} · S$${String.format(Locale.US, "%.2f", claim.amount)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isApproved) tealAccent else slatePendingColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSummarySheet(
    monthTitle: String, // e.g. "September 2026"
    approvedLeaveDays: Double,
    pendingLeaveDays: Double,
    approvedOtHours: Double,
    pendingOtHours: Double,
    approvedClaimAmount: Double,
    pendingClaimAmount: Double,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val slatePendingColor = diBadgeColors.tabInactive

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "$monthTitle summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Table Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("", modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = tealAccent) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approved", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = slatePendingColor) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pending", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Leave Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Leave", fontSize = 15.sp, color = primaryTextColor, modifier = Modifier.weight(1f))
                Text("${approvedLeaveDays.toInt()} days", fontSize = 14.sp, color = primaryTextColor, modifier = Modifier.weight(1f), alignment = Alignment.Center)
                Text("${pendingLeaveDays.toInt()} days", fontSize = 14.sp, color = primaryTextColor, modifier = Modifier.weight(1f), alignment = Alignment.CenterEnd)
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Extra hours Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Extra hours", fontSize = 15.sp, color = primaryTextColor, modifier = Modifier.weight(1f))
                Text("$approvedOtHours hrs", fontSize = 14.sp, color = primaryTextColor, modifier = Modifier.weight(1f), alignment = Alignment.Center)
                Text("$pendingOtHours hrs", fontSize = 14.sp, color = primaryTextColor, modifier = Modifier.weight(1f), alignment = Alignment.CenterEnd)
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Claims Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Claims", fontSize = 15.sp, color = primaryTextColor, modifier = Modifier.weight(1f))
                Text("S$${String.format(Locale.US, "%.2f", approvedClaimAmount)}", fontSize = 14.sp, color = primaryTextColor, modifier = Modifier.weight(1f), alignment = Alignment.Center)
                Text("S$${String.format(Locale.US, "%.2f", pendingClaimAmount)}", fontSize = 14.sp, color = primaryTextColor, modifier = Modifier.weight(1f), alignment = Alignment.CenterEnd)
            }
        }
    }
}

@Composable
private fun Text(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier,
    alignment: Alignment
) {
    Box(modifier = modifier, contentAlignment = alignment) {
        Text(text = text, fontSize = fontSize, color = color)
    }
}
