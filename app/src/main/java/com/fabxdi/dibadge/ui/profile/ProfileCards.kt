package com.fabxdi.dibadge.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.util.avatarColorFor
import com.fabxdi.dibadge.util.capitalizeWords
import com.fabxdi.dibadge.util.isValidName
import com.fabxdi.dibadge.viewmodel.ProfileViewModel
import com.fabxdi.dibadge.viewmodel.UserProfileData
import com.fabxdi.dibadge.viewmodel.UsernameAvailabilityState

@Composable
fun ProfileAvatarSection(
    profileData: UserProfileData,
    isSaving: Boolean,
    isUploadingPhoto: Boolean = false,
    pageBgColor: Color,
    cardBgColor: Color,
    secondaryTextColor: Color,
    primaryTextColor: Color,
    onAvatarClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clickable(enabled = !isSaving && !isUploadingPhoto) { onAvatarClick() },
        contentAlignment = Alignment.Center
    ) {
        if (profileData.photoUrl.isNotBlank()) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape
            ) {
                AsyncImage(
                    model = profileData.photoUrl,
                    contentDescription = "Profile photo, double tap to change",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            val avatarInitial = profileData.firstName.trim().take(1).ifBlank { "U" }.uppercase()
            val solidBgColor = remember(profileData.firstName, profileData.username) {
                avatarColorFor(profileData.firstName.ifBlank { profileData.username })
            }
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = solidBgColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarInitial,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = cardBgColor,
            border = BorderStroke(1.dp, pageBgColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Change photo badge",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        if (isUploadingPhoto) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.40f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileNameCard(
    profileData: UserProfileData,
    viewModel: ProfileViewModel,
    isSaving: Boolean,
    cardBgColor: Color,
    dividerColor: Color,
    focusManager: FocusManager
) {
    val firstNameError = remember(profileData.firstName) {
        if (profileData.firstName.isNotBlank() && !isValidName(profileData.firstName)) "Use letters only" else null
    }
    val middleNameError = remember(profileData.middleName) {
        if (profileData.middleName.isNotBlank() && !isValidName(profileData.middleName)) "Use letters only" else null
    }
    val lastNameError = remember(profileData.lastName) {
        if (profileData.lastName.isNotBlank() && !isValidName(profileData.lastName)) "Use letters only" else null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ProfileCardRow(
                label = "First name",
                value = profileData.firstName,
                onValueChange = { viewModel.updateFirstName(it) },
                errorMessage = firstNameError,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            ProfileCardRow(
                label = "Middle name",
                value = profileData.middleName,
                onValueChange = { viewModel.updateMiddleName(it) },
                errorMessage = middleNameError,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            ProfileCardRow(
                label = "Last name",
                value = profileData.lastName,
                onValueChange = { viewModel.updateLastName(it) },
                errorMessage = lastNameError,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
        }
    }
}

@Composable
fun ProfileUsernameCard(
    profileData: UserProfileData,
    viewModel: ProfileViewModel,
    usernameAvailability: UsernameAvailabilityState,
    isSaving: Boolean,
    cardBgColor: Color,
    dividerColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    tealAccent: Color,
    errorColor: Color,
    focusManager: FocusManager
) {
    val isUsernameLocked = viewModel.isUsernameLocked()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isUsernameLocked) {
                ProfileCardRow(
                    label = "Username",
                    value = profileData.username,
                    onValueChange = {},
                    prefixText = "@",
                    enabled = false,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Username, @${profileData.username}, cannot be changed",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val isUsernameError = usernameAvailability is UsernameAvailabilityState.InvalidFormat ||
                            usernameAvailability is UsernameAvailabilityState.Reserved ||
                            usernameAvailability is UsernameAvailabilityState.Taken

                    val statusMessage = when (usernameAvailability) {
                        is UsernameAvailabilityState.InvalidFormat -> "Use 3 to 20 letters, numbers, dots or underscores"
                        is UsernameAvailabilityState.Reserved -> "That username isn't available"
                        is UsernameAvailabilityState.Checking -> "Checking…"
                        is UsernameAvailabilityState.Available -> "Available"
                        is UsernameAvailabilityState.Taken -> "That username is taken"
                        is UsernameAvailabilityState.CantCheck -> "Can't check right now. Check your connection."
                        else -> null
                    }

                    ProfileCardRow(
                        label = "Username",
                        value = profileData.username,
                        onValueChange = { viewModel.updateUsername(it) },
                        prefixText = "@",
                        errorMessage = if (isUsernameError) statusMessage else null,
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        trailingIcon = {
                            when (usernameAvailability) {
                                is UsernameAvailabilityState.Checking -> {
                                    CircularProgressIndicator(
                                        color = tealAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                is UsernameAvailabilityState.Available -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Available",
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                is UsernameAvailabilityState.InvalidFormat,
                                is UsernameAvailabilityState.Reserved,
                                is UsernameAvailabilityState.Taken -> {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Error",
                                        tint = errorColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                else -> {}
                            }
                        }
                    )

                    if (usernameAvailability is UsernameAvailabilityState.CantCheck) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Can't check right now. Check your connection.",
                                fontSize = 12.sp,
                                color = errorColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Retry",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = tealAccent,
                                modifier = Modifier
                                    .clickable { viewModel.retryUsernameCheck() }
                                    .padding(start = 8.dp)
                            )
                        }
                    }

                    if (usernameAvailability is UsernameAvailabilityState.Checking || usernameAvailability is UsernameAvailabilityState.Available) {
                        Text(
                            text = statusMessage ?: "",
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }

                    if (usernameAvailability is UsernameAvailabilityState.Taken) {
                        val suggestions = (usernameAvailability as UsernameAvailabilityState.Taken).suggestions
                        if (suggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                suggestions.take(3).forEach { suggestion ->
                                    Surface(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .clickable { viewModel.updateUsername(suggestion) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, dividerColor)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "@$suggestion",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = primaryTextColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            val effectiveFirstName = remember(profileData.firstName) { profileData.firstName.capitalizeWords() }
            Column(modifier = Modifier.fillMaxWidth()) {
                ProfileCardRow(
                    label = "Nickname",
                    value = profileData.nickname,
                    onValueChange = { viewModel.updateNickname(it) },
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                if (profileData.nickname.isBlank()) {
                    Text(
                        text = "Empty? Your first name is used on the Home banner",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor,
                        modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileWorkCard(
    profileData: UserProfileData,
    viewModel: ProfileViewModel,
    isSaving: Boolean,
    cardBgColor: Color,
    dividerColor: Color,
    focusManager: FocusManager
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ProfileCardRow(
                label = "Company",
                value = profileData.company,
                onValueChange = { viewModel.updateCompany(it) },
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            ProfileCardRow(
                label = "Role",
                value = profileData.role,
                onValueChange = { viewModel.updateRole(it) },
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
        }
    }
}

@Composable
fun ProfileContactCard(
    profileData: UserProfileData,
    viewModel: ProfileViewModel,
    isSaving: Boolean,
    cardBgColor: Color,
    dividerColor: Color,
    secondaryTextColor: Color,
    focusManager: FocusManager,
    onShowCountrySheet: () -> Unit,
    onShowDatePickerDialog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ProfileCardRow(
                label = "Email",
                value = profileData.email,
                onValueChange = {},
                enabled = false,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Email, cannot be changed",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            ProfileCardRow(
                label = "Phone",
                value = profileData.phone,
                onValueChange = { viewModel.updatePhone(it) },
                enabled = !isSaving,
                leadingContent = {
                    CountryChip(
                        code = profileData.phoneCountryCode.ifBlank { "+65" },
                        onClick = { if (!isSaving) onShowCountrySheet() }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            ProfileCardRow(
                label = "Birthday",
                value = profileData.birthday,
                onValueChange = {},
                readOnly = true,
                enabled = !isSaving,
                onFieldClick = { if (!isSaving) onShowDatePickerDialog() },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Select Birthday",
                        tint = secondaryTextColor,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(enabled = !isSaving) { onShowDatePickerDialog() }
                    )
                }
            )
        }
    }
}

@Composable
fun ProfileBioCard(
    profileData: UserProfileData,
    viewModel: ProfileViewModel,
    isSaving: Boolean,
    cardBgColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    tealAccent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Bio",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(2.dp))

                BasicTextField(
                    value = profileData.bio,
                    onValueChange = { viewModel.updateBio(it) },
                    enabled = !isSaving,
                    singleLine = false,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    ),
                    cursorBrush = SolidColor(tealAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            Text(
                text = "${profileData.bio.length} / 150",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = tabInactiveColor,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun ProfileSignatureCard(
    profileData: UserProfileData,
    isSaving: Boolean,
    cardBgColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    onShowSignatureDialog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Signature",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            val isDark = MaterialTheme.colorScheme.background.red < 0.5f
            val wellBgColor = if (isDark) Color(0xFF121519) else Color(0xFFE2E6E9)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clickable(enabled = !isSaving) { onShowSignatureDialog() },
                shape = RoundedCornerShape(10.dp),
                color = wellBgColor
            ) {
                if (profileData.signatureUrl.isNotBlank()) {
                    val parsedSigBitmap = remember(profileData.signatureUrl) { parseSignatureImageBitmap(profileData.signatureUrl) }
                    val sigTintColor = if (isDark) Color(0xFFFAF9F5) else Color(0xFF1C1B1F)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    ) {
                        if (parsedSigBitmap != null) {
                            Image(
                                bitmap = parsedSigBitmap,
                                contentDescription = "Signature",
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(sigTintColor, BlendMode.SrcIn),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = profileData.signatureUrl,
                                contentDescription = "Signature",
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(sigTintColor, BlendMode.SrcIn),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = tabInactiveColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add signature",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = tabInactiveColor
                        )
                    }
                }
            }
        }
    }
}
