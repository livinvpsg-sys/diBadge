package com.fabxdi.dibadge.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.ProfileUiState
import com.fabxdi.dibadge.viewmodel.ProfileViewModel
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val emailVerifyState by viewModel.emailVerifyState.collectAsState()
    val usernameAvailability by viewModel.usernameAvailability.collectAsState()
    val isUploadingPhoto by viewModel.isUploadingPhoto.collectAsState()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showChoosePhotoSheet by remember { mutableStateOf(false) }
    var showPhotoViewer by remember { mutableStateOf(false) }
    var cropImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraPermissionDeniedDialog by remember { mutableStateOf(false) }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showVerifyEmailFirstDialog by remember { mutableStateOf(false) }
    var showUsernameConfirmDialog by remember { mutableStateOf(false) }
    var showPhoneCountrySheet by remember { mutableStateOf(false) }
    var showCountryPickerSheet by remember { mutableStateOf(false) }

    var savedSignatureMode by remember { mutableStateOf("draw") }
    var savedTypedName by remember { mutableStateOf("") }
    var savedStrokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }

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

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val uri = pendingCameraUri
            if (success && uri != null) {
                cropImageUri = uri
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                val tempFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                pendingCameraUri = uri
                takePhotoLauncher.launch(uri)
            } else {
                showCameraPermissionDeniedDialog = true
            }
        }
    )

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                cropImageUri = uri
            }
        }
    )

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val dividerColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tabInactiveColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    LaunchedEffect(Unit) {
        viewModel.observeNetworkAndAutoRetry(context)
    }

    val isSaveEnabled = viewModel.isSaveEnabled()

    val onPerformSave = {
        viewModel.saveProfile(
            onVerifyRequired = {},
            onSuccess = {
                snackbarMessage = "Profile saved"
            },
            onError = { msg ->
                if (msg == "VERIFY_EMAIL_FIRST") {
                    showVerifyEmailFirstDialog = true
                } else if (msg == "USERNAME_TAKEN") {
                    snackbarMessage = "That username is taken"
                } else if (msg == "USERNAME_LOCKED") {
                    snackbarMessage = "Your username is already set"
                } else if (msg == "NETWORK_ERROR") {
                    snackbarMessage = "Couldn't reach the server. Check your connection and try again."
                } else if (msg.contains("INVALID")) {
                    snackbarMessage = "Please fix errors before saving"
                } else {
                    snackbarMessage = msg
                }
            }
        )
    }

    val clipboardManager = LocalClipboardManager.current

    val handleCopyBadgeNumber: (String) -> Unit = { code ->
        if (code.isNotBlank()) {
            val cleanCode = code.replace(" ", "").trim().uppercase()
            clipboardManager.setText(AnnotatedString(cleanCode))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                snackbarMessage = "Badge number copied"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBgColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = primaryTextColor
                )
            },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor
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
                            color = tealAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (hasChanges) {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                if (!viewModel.isUsernameLocked() && profileData.username.isNotBlank()) {
                                    showUsernameConfirmDialog = true
                                } else {
                                    onPerformSave()
                                }
                            },
                            enabled = isSaveEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = tealAccent,
                                disabledContentColor = tabInactiveColor
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
                containerColor = pageBgColor
            )
        )

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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- AVATAR SECTION ---
                    ProfileAvatarSection(
                        profileData = profileData,
                        isSaving = isSaving,
                        isUploadingPhoto = isUploadingPhoto,
                        pageBgColor = pageBgColor,
                        cardBgColor = cardBgColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        onAvatarClick = {
                            focusManager.clearFocus()
                            if (profileData.photoUrl.isNotBlank()) {
                                showPhotoViewer = true
                            } else {
                                showChoosePhotoSheet = true
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- CARDS SECTION ---

                    // CARD 1: Names
                    ProfileNameCard(
                        profileData = profileData,
                        viewModel = viewModel,
                        isSaving = isSaving,
                        cardBgColor = cardBgColor,
                        dividerColor = dividerColor,
                        focusManager = focusManager
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // CARD 2: Badge Number, Username & Nickname
                    ProfileUsernameCard(
                        profileData = profileData,
                        viewModel = viewModel,
                        usernameAvailability = usernameAvailability,
                        isSaving = isSaving,
                        cardBgColor = cardBgColor,
                        dividerColor = dividerColor,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        tabInactiveColor = tabInactiveColor,
                        tealAccent = tealAccent,
                        errorColor = errorColor,
                        focusManager = focusManager,
                        onCopyBadgeNumberClick = handleCopyBadgeNumber
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // CARD 3: Bio (after Nickname)
                    ProfileBioCard(
                        profileData = profileData,
                        viewModel = viewModel,
                        isSaving = isSaving,
                        cardBgColor = cardBgColor,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        tabInactiveColor = tabInactiveColor,
                        tealAccent = tealAccent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // CARD 4: Email, Phone, Country, Birthday (after Bio)
                    ProfileContactCard(
                        profileData = profileData,
                        viewModel = viewModel,
                        isSaving = isSaving,
                        cardBgColor = cardBgColor,
                        dividerColor = dividerColor,
                        secondaryTextColor = secondaryTextColor,
                        focusManager = focusManager,
                        onShowPhoneCountrySheet = { showPhoneCountrySheet = true },
                        onShowCountryPickerSheet = { showCountryPickerSheet = true },
                        onShowDatePickerDialog = { showDatePickerDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // CARD 5: Work (Organization, Role)
                    ProfileWorkCard(
                        profileData = profileData,
                        viewModel = viewModel,
                        isSaving = isSaving,
                        cardBgColor = cardBgColor,
                        dividerColor = dividerColor,
                        focusManager = focusManager
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // CARD 6: Signature
                    ProfileSignatureCard(
                        profileData = profileData,
                        isSaving = isSaving,
                        cardBgColor = cardBgColor,
                        secondaryTextColor = secondaryTextColor,
                        tabInactiveColor = tabInactiveColor,
                        onShowSignatureDialog = { showSignatureDialog = true }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

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
                    Text("Discard", color = errorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showUsernameConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameConfirmDialog = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Use @${profileData.username}?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "You can't change your username later.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUsernameConfirmDialog = false
                        onPerformSave()
                    }
                ) {
                    Text(
                        text = "Confirm",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealAccent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameConfirmDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                }
            }
        )
    }

    if (showVerifyEmailFirstDialog) {
        AlertDialog(
            onDismissRequest = { showVerifyEmailFirstDialog = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Verify email first",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "Verify your email address first.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(onClick = { showVerifyEmailFirstDialog = false }) {
                    Text(
                        text = "OK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealAccent
                    )
                }
            }
        )
    }

    // Phone Country Code Sheet
    if (showPhoneCountrySheet) {
        CountryPickerSheet(
            selectedDialCode = profileData.phoneCountryCode.ifBlank { "+65" },
            showDialCode = true,
            onCountrySelected = { country ->
                viewModel.updatePhoneCountryCode(country.dialCode)
            },
            onDismiss = { showPhoneCountrySheet = false }
        )
    }

    // Residence Country Sheet
    if (showCountryPickerSheet) {
        CountryPickerSheet(
            selectedIsoCode = profileData.country,
            showDialCode = false,
            onCountrySelected = { country ->
                viewModel.updateCountry(country.isoCode)
            },
            onClearCountryClick = {
                viewModel.updateCountry("")
            },
            onDismiss = { showCountryPickerSheet = false }
        )
    }

    // Choose Photo Sheet
    if (showChoosePhotoSheet) {
        ChoosePhotoSheet(
            onTakePhotoClick = {
                val hasCamPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasCamPermission) {
                    val tempFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                    pendingCameraUri = uri
                    takePhotoLauncher.launch(uri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onChooseGalleryClick = {
                galleryPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showChoosePhotoSheet = false }
        )
    }

    // Photo Viewer
    if (showPhotoViewer && profileData.photoUrl.isNotBlank()) {
        ProfilePhotoViewer(
            photoUrl = profileData.photoUrl,
            onCloseClick = { showPhotoViewer = false },
            onEditClick = {
                showChoosePhotoSheet = true
            },
            onDeleteClick = {
                viewModel.deletePhoto(context) { success ->
                    showPhotoViewer = false
                    if (success) {
                        snackbarMessage = "Photo removed"
                    } else {
                        snackbarMessage = "Couldn't remove the photo. Try again."
                    }
                }
            }
        )
    }

    // Crop Screen
    val activeCropUri = cropImageUri
    if (activeCropUri != null) {
        ProfilePhotoCropScreen(
            imageUri = activeCropUri,
            onCancelClick = { cropImageUri = null },
            onDoneCrop = { croppedBitmap ->
                cropImageUri = null
                viewModel.saveCroppedPhoto(context, croppedBitmap) { success, msg ->
                    if (success) {
                        snackbarMessage = "Photo updated"
                    } else {
                        snackbarMessage = msg ?: "Couldn't update the photo. Try again."
                    }
                }
            }
        )
    }

    // Camera Access Off Dialog
    if (showCameraPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDeniedDialog = false },
            title = {
                Text(
                    text = "Camera access is off",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "Allow the camera in Settings to take a photo. You can still choose one from your gallery.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraPermissionDeniedDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold, color = tealAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraPermissionDeniedDialog = false }) {
                    Text("Not now", fontWeight = FontWeight.Normal, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp)
        )
    }

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

    if (emailVerifyState.isOpen) {
        VerifyYourEmailSheet(
            email = profileData.email,
            verificationState = emailVerifyState,
            onTypedCodeChange = { viewModel.updateTypedCode(it) },
            onVerifyClick = {
                viewModel.verifyCode(
                    onSuccess = {
                        snackbarMessage = "Profile saved"
                    }
                )
            },
            onResendClick = {
                viewModel.resendCode(
                    onSent = {
                        snackbarMessage = "New code sent"
                    }
                )
            },
            onDismiss = { viewModel.dismissEmailVerifySheet() }
        )
    }
}
