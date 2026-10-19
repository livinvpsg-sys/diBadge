package com.fabxdi.dibadge.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.CountryData
import com.fabxdi.dibadge.util.CountryInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selectedIsoCode: String = "",
    selectedDialCode: String = "",
    showDialCode: Boolean = false,
    onCountrySelected: (CountryInfo) -> Unit,
    onClearCountryClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allCountries = remember { CountryData.allCountries }

    val filteredCountries = remember(searchQuery, allCountries) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allCountries
        } else {
            val cleanQ = if (q.startsWith("+")) q.substring(1) else q
            allCountries.filter { c ->
                c.name.lowercase().contains(q) ||
                        c.isoCode.lowercase() == q ||
                        c.dialCode.contains(cleanQ) ||
                        c.dialCode.substringAfter("+").contains(cleanQ)
            }
        }
    }

    val listState = rememberLazyListState()

    // Scroll selected country into view on open
    LaunchedEffect(Unit) {
        if (showDialCode && selectedDialCode.isNotBlank()) {
            val cleanDial = if (selectedDialCode.startsWith("+")) selectedDialCode else "+$selectedDialCode"
            val index = filteredCountries.indexOfFirst { it.dialCode == cleanDial }
            if (index >= 0) listState.scrollToItem(index)
        } else if (!showDialCode && selectedIsoCode.isNotBlank()) {
            val index = filteredCountries.indexOfFirst { it.isoCode.equals(selectedIsoCode, ignoreCase = true) }
            if (index >= 0) {
                listState.scrollToItem(if (onClearCountryClick != null) index + 1 else index)
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val cardBgColor = colors.surface
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tealAccent = diBadgeColors.accent
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
        ) {
            // Search Field Tile
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (showDialCode) "Search country or code" else "Search country",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            ),
                            cursorBrush = SolidColor(tealAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 7 Visible Rows Height (7 * 50dp = 350dp)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                // "No country" Row if editing Country and one is set
                if (!showDialCode && onClearCountryClick != null && selectedIsoCode.isNotBlank() && searchQuery.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clickable {
                                    onClearCountryClick()
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No country",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = outlineColor.copy(alpha = 0.3f))
                    }
                }

                items(filteredCountries, key = { "${it.isoCode}_${it.dialCode}" }) { country ->
                    val isSelected = if (showDialCode) {
                        val cleanDial = if (selectedDialCode.startsWith("+")) selectedDialCode else "+$selectedDialCode"
                        country.dialCode == cleanDial
                    } else {
                        country.isoCode.equals(selectedIsoCode, ignoreCase = true)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable {
                                onCountrySelected(country)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = country.flagEmoji,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = country.name,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = primaryTextColor
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showDialCode) {
                                Text(
                                    text = country.dialCode,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = tealAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = outlineColor.copy(alpha = 0.3f))
                }
            }
        }
    }
}
