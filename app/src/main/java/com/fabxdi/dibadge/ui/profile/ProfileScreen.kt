package com.fabxdi.dibadge.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.capitalizeWords
import com.fabxdi.dibadge.viewmodel.ProfileUiState
import com.fabxdi.dibadge.viewmodel.ProfileViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun parseSignatureImageBitmap(sigUrl: String): ImageBitmap? {
    if (sigUrl.isBlank()) return null
    return try {
        if (sigUrl.startsWith("data:image")) {
            val base64Str = sigUrl.substringAfter("base64,")
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            bitmap?.asImageBitmap()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

// Theme Color Tokens
val ProfileBackgroundColor = Color(0xFF262624)
val ProfileTextColor = Color(0xFFFAF9F5)
val ProfileLabelColor = Color(0xFFA8A69E)
val ProfileFieldLineColor = Color(0xFF5A5955)
val ProfileDisabledTextColor = Color(0xFF77777C)
val ProfileDisabledLineColor = Color(0xFF3A3A37)
val ProfileAccentTeal = Color(0xFF14B8A6)
val ProfileErrorColor = Color(0xFFFF8A80)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val profileData by viewModel.profileData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showPhotoOptionsSheet by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }

    var savedSignatureMode by remember { mutableStateOf("draw") }
    var savedTypedName by remember { mutableStateOf("") }
    var savedStrokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }

    var snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val hasChanges = viewModel.hasChanges()
    val isSaving = uiState is ProfileUiState.Saving

    val handleBack = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onBackClick()
        }
    }

    BackHandler(onBack = handleBack)

    // Photo Launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.uploadPhoto(context, uri) { success, msg ->
                    if (!success && msg != null) {
                        snackbarMessage = msg
                    }
                }
            }
        }
    )

    val profileBgColor = MaterialTheme.colorScheme.background
    val profileTextColor = MaterialTheme.colorScheme.onBackground
    val profileLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val profileFieldLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val profileDisabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val profileDisabledLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val profileAccentTeal = MaterialTheme.colorScheme.primary
    val profileErrorColor = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(profileBgColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // 1. Top Bar ("Profile", Back Arrow, "Save")
        TopAppBar(
            title = {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = profileTextColor
                )
            },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = profileTextColor
                    )
                }
            },
            actions = {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = profileAccentTeal,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.saveProfile(
                                    onSuccess = {
                                        snackbarMessage = "Profile saved successfully"
                                    },
                                    onError = { msg ->
                                        snackbarMessage = msg
                                    }
                                )
                            },
                            enabled = hasChanges && !isSaving,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = profileAccentTeal,
                                disabledContentColor = profileAccentTeal.copy(alpha = 0.38f)
                            )
                        ) {
                            Text(
                                text = "Save",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = profileBgColor
            )
        )

        // 2. Main Scrollable Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Column constrained to max 420dp width
                Column(
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- AVATAR & PHOTO SECTION ---
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 76dp Circle Avatar
                        if (profileData.photoUrl.isNotBlank()) {
                            Surface(
                                modifier = Modifier.size(76.dp),
                                shape = CircleShape
                            ) {
                                AsyncImage(
                                    model = profileData.photoUrl,
                                    contentDescription = "Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            val avatarInitial = profileData.firstName.trim().take(1).ifBlank { "U" }.uppercase()
                            val avatarGradient = remember {
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF5CC9C0), Color(0xFF17645E))
                                )
                            }
                            Surface(
                                modifier = Modifier.size(76.dp),
                                shape = CircleShape,
                                color = Color.Transparent
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(brush = avatarGradient, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = avatarInitial,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEDEDED)
                                    )
                                }
                            }
                        }

                        // 28dp Camera Badge (bottom-right of avatar)
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.BottomEnd)
                                .clickable(enabled = !isSaving) { showPhotoOptionsSheet = true },
                            shape = CircleShape,
                            color = profileBgColor,
                            border = BorderStroke(1.5.dp, profileFieldLineColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = "Change photo badge",
                                    tint = profileLabelColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // "Change photo" Text Button
                    Text(
                        text = "Change photo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = profileAccentTeal,
                        modifier = Modifier
                            .clickable(enabled = !isSaving) { showPhotoOptionsSheet = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- FIELDS SECTION ---

                    // 1. First Name & Last Name (Side by side, 20dp gap, equal width)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        ProfileUnderlineField(
                            label = "First name",
                            value = profileData.firstName,
                            onValueChange = { viewModel.updateFirstName(it) },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) })
                        )

                        ProfileUnderlineField(
                            label = "Last name",
                            value = profileData.lastName,
                            onValueChange = { viewModel.updateLastName(it) },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Username (@ prefix, 3-20 chars)
                    ProfileUnderlineField(
                        label = "Username",
                        value = profileData.username,
                        onValueChange = { viewModel.updateUsername(it) },
                        prefixText = "@",
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Nickname (Home banner greeting name)
                    val effectiveFirstName = remember(profileData.firstName) { profileData.firstName.capitalizeWords() }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileUnderlineField(
                            label = "Nickname",
                            value = profileData.nickname,
                            onValueChange = { viewModel.updateNickname(it) },
                            hint = if (profileData.nickname.isBlank()) effectiveFirstName.ifBlank { "First name" } else "",
                            enabled = !isSaving,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                        if (profileData.nickname.isBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Empty? Your first name is used on the Home banner",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = ProfileLabelColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Email (Read-only, taken from signed-in account)
                    ProfileUnderlineField(
                        label = "Email",
                        value = profileData.email,
                        onValueChange = {},
                        enabled = false,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Email, cannot be changed",
                                tint = ProfileDisabledTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Phone (International format)
                    ProfileUnderlineField(
                        label = "Phone",
                        value = profileData.phone,
                        onValueChange = { viewModel.updatePhone(it) },
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6. Birthday (DatePicker)
                    ProfileUnderlineField(
                        label = "Birthday",
                        value = profileData.birthday,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSaving,
                        onFieldClick = { if (!isSaving) showDatePickerDialog = true },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = "Select Birthday",
                                tint = ProfileLabelColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(enabled = !isSaving) { showDatePickerDialog = true }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 7. Bio (Multi-line, up to 150 chars with character counter)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileUnderlineField(
                            label = "Bio",
                            value = profileData.bio,
                            onValueChange = { viewModel.updateBio(it) },
                            enabled = !isSaving,
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${profileData.bio.length} / 150",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = ProfileLabelColor,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 8. Signature Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Signature",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = ProfileLabelColor
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (profileData.signatureUrl.isNotBlank()) {
                            // Saved Signature Box (110dp height for ample vertical room)
                            val parsedSigBitmap = remember(profileData.signatureUrl) { parseSignatureImageBitmap(profileData.signatureUrl) }
                            val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f
                            val sigTintColor = if (isDarkTheme) Color(0xFFFAF9F5) else Color(0xFF1C1B1F)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clickable(enabled = !isSaving) { showSignatureDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, profileDisabledLineColor)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .semantics {
                                            contentDescription = "Your saved signature"
                                        }
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
                            }
                        } else {
                            // Empty Signature Box (110dp height)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clickable(enabled = !isSaving) { showSignatureDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.5.dp, profileFieldLineColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = ProfileLabelColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Add signature",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = ProfileLabelColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Unsaved Changes Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text("Discard", color = ProfileErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Photo Options Sheet / Dialog
    if (showPhotoOptionsSheet) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsSheet = false },
            title = { Text("Profile Photo") },
            text = {
                Column {
                    Text(
                        text = "Choose photo from gallery",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoOptionsSheet = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 12.dp)
                    )
                    if (profileData.photoUrl.isNotBlank()) {
                        HorizontalDivider(thickness = 0.5.dp)
                        Text(
                            text = "Remove photo",
                            fontSize = 16.sp,
                            color = ProfileErrorColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPhotoOptionsSheet = false
                                    viewModel.removePhoto()
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoOptionsSheet = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Birthday DatePicker Sheet
    if (showDatePickerDialog) {
        val initialBirthday = try {
            if (profileData.birthday.isNotBlank()) {
                LocalDate.parse(profileData.birthday, DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
            } else null
        } catch (e: Exception) { null }

        DiBadgeDatePicker(
            selectedDate = initialBirthday,
            maxDate = LocalDate.now(),
            minDate = LocalDate.now().minusYears(120),
            title = "Select Birthday",
            onDateSelected = { chosenDate ->
                showDatePickerDialog = false
                val formatted = chosenDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
                viewModel.updateBirthday(formatted)
            },
            onDismiss = { showDatePickerDialog = false }
        )
    }

    // Signature Drawing / Typing Dialog
    if (showSignatureDialog) {
        SignatureDialog(
            initialMode = savedSignatureMode,
            initialTypedName = savedTypedName,
            initialStrokes = savedStrokes,
            onDismiss = { showSignatureDialog = false },
            onSignatureSaved = { sigDataUrl, mode, typedNameVal, strokesList ->
                showSignatureDialog = false
                savedSignatureMode = mode
                savedTypedName = typedNameVal
                savedStrokes = strokesList
                viewModel.updateSignatureUrl(sigDataUrl)
            }
        )
    }
}

@Composable
fun ProfileUnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    prefixText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    onFieldClick: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val labelColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val textColor = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val fieldLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val disabledLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val accentColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1.0f else 0.5f)
    ) {
        // Label above
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = labelColor
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clickable(enabled = onFieldClick != null && enabled) { onFieldClick?.invoke() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (prefixText != null) {
                    Text(
                        text = prefixText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = labelColor
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && hint.isNotEmpty()) {
                        Text(
                            text = hint,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = labelColor
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled && !readOnly,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = textColor
                        ),
                        cursorBrush = SolidColor(accentColor),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                    )
                }

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }

        // Underline (1dp -> 2dp when focused -> 2dp on error)
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = if (errorMessage != null || isFocused) 2.dp else 1.dp,
            color = when {
                !enabled -> disabledLineColor
                errorMessage != null -> errorColor
                isFocused -> accentColor
                else -> fieldLineColor
            }
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = errorColor,
                modifier = Modifier.semantics {
                    error(errorMessage)
                }
            )
        }
    }
}

fun createSignatureDataUrl(
    width: Int,
    height: Int,
    strokes: List<List<Offset>>,
    typedText: String,
    mode: String,
    strokeColorHex: String = "#FAF9F5"
): String {
    val rawWidth = if (width > 0) width else 1200
    val rawHeight = if (height > 0) height else 800
    val tempBitmap = Bitmap.createBitmap(rawWidth, rawHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(tempBitmap)

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    if (mode == "draw") {
        val paint = Paint().apply {
            color = android.graphics.Color.parseColor(strokeColorHex)
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.size > 1) {
                val path = android.graphics.Path()
                path.moveTo(stroke[0].x, stroke[0].y)
                for (pt in stroke) {
                    minX = minOf(minX, pt.x)
                    minY = minOf(minY, pt.y)
                    maxX = maxOf(maxX, pt.x)
                    maxY = maxOf(maxY, pt.y)
                }
                for (i in 1 until stroke.size) {
                    path.lineTo(stroke[i].x, stroke[i].y)
                }
                canvas.drawPath(path, paint)
            }
        }
    } else {
        val textPaint = Paint().apply {
            color = android.graphics.Color.parseColor(strokeColorHex)
            isAntiAlias = true
            textSize = 72f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        val textToDraw = if (typedText.isNotBlank()) typedText else "Signature"
        val x = rawWidth / 2f
        val y = rawHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(textToDraw, x, y, textPaint)

        val bounds = Rect()
        textPaint.getTextBounds(textToDraw, 0, textToDraw.length, bounds)
        minX = x - bounds.width() / 2f - 10f
        maxX = x + bounds.width() / 2f + 10f
        minY = y + bounds.top - 10f
        maxY = y + bounds.bottom + 10f
    }

    val finalBitmap = if (minX < maxX && minY < maxY) {
        val pad = 20
        val cropX = maxOf(0, (minX - pad).toInt())
        val cropY = maxOf(0, (minY - pad).toInt())
        val cropW = minOf(rawWidth - cropX, (maxX - minX + pad * 2).toInt())
        val cropH = minOf(rawHeight - cropY, (maxY - minY + pad * 2).toInt())
        if (cropW > 10 && cropH > 10) {
            Bitmap.createBitmap(tempBitmap, cropX, cropY, cropW, cropH)
        } else {
            tempBitmap
        }
    } else {
        tempBitmap
    }

    val baos = ByteArrayOutputStream()
    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
    val bytes = baos.toByteArray()
    return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@Composable
fun SignatureDialog(
    initialMode: String = "draw",
    initialTypedName: String = "",
    initialStrokes: List<List<Offset>> = emptyList(),
    onDismiss: () -> Unit,
    onSignatureSaved: (sigDataUrl: String, mode: String, typedName: String, strokes: List<List<Offset>>) -> Unit
) {
    var selectedMode by remember { mutableStateOf(initialMode.ifBlank { "draw" }) }
    var typedName by remember { mutableStateOf(initialTypedName) }

    val currentStroke = remember { mutableStateListOf<Offset>() }
    val allStrokes = remember { mutableStateListOf<List<Offset>>().apply { addAll(initialStrokes) } }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val strokeColorHex = if (isDark) "#FAF9F5" else "#1C1B1F"
    val strokeColor = if (isDark) Color(0xFFFAF9F5) else Color(0xFF1C1B1F)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Toggle between "Draw" and "Type"
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedMode == "draw",
                        onClick = { selectedMode = "draw" },
                        label = { Text("Draw", fontWeight = if (selectedMode == "draw") FontWeight.Bold else FontWeight.Normal) },
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    FilterChip(
                        selected = selectedMode == "type",
                        onClick = { selectedMode = "type" },
                        label = { Text("Type", fontWeight = if (selectedMode == "type") FontWeight.Bold else FontWeight.Normal) },
                        shape = CircleShape
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedMode == "draw") {
                    // DRAW MODE
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Draw your signature below:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStroke.clear()
                                            currentStroke.add(offset)
                                        },
                                        onDrag = { change, _ ->
                                            currentStroke.add(change.position)
                                        },
                                        onDragEnd = {
                                            if (currentStroke.isNotEmpty()) {
                                                allStrokes.add(currentStroke.toList())
                                                currentStroke.clear()
                                            }
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (stroke in allStrokes) {
                                    for (i in 0 until stroke.size - 1) {
                                        drawLine(
                                            color = strokeColor,
                                            start = stroke[i],
                                            end = stroke[i + 1],
                                            strokeWidth = 3.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                                for (i in 0 until currentStroke.size - 1) {
                                    drawLine(
                                        color = strokeColor,
                                        start = currentStroke[i],
                                        end = currentStroke[i + 1],
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // TYPE MODE
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Type your signature:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = typedName,
                            onValueChange = { typedName = it },
                            placeholder = { Text("Your Name", fontFamily = FontFamily.Cursive) },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 26.sp,
                                fontFamily = FontFamily.Cursive,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalStrokes = if (selectedMode == "draw") {
                        if (currentStroke.isNotEmpty()) allStrokes + listOf(currentStroke.toList()) else allStrokes.toList()
                    } else {
                        emptyList()
                    }

                    val finalTypedName = if (selectedMode == "type") typedName.trim() else ""

                    val sigDataUrl = createSignatureDataUrl(
                        width = 1200,
                        height = 800,
                        strokes = finalStrokes,
                        typedText = finalTypedName,
                        mode = selectedMode,
                        strokeColorHex = strokeColorHex
                    )

                    onSignatureSaved(sigDataUrl, selectedMode, finalTypedName, finalStrokes)
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (selectedMode == "draw") {
                        currentStroke.clear()
                        allStrokes.clear()
                    } else {
                        typedName = ""
                    }
                }
            ) {
                Text(
                    text = "Clear",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
