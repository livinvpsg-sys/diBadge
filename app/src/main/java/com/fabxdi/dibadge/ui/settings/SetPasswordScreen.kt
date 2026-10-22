package com.fabxdi.dibadge.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordScreen(
    userEmail: String = "",
    hasExistingPassword: Boolean = false,
    onBack: () -> Unit,
    onPasswordSavedSuccessfully: (isNew: Boolean) -> Unit,
    onGoogleReauthRequest: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isOldPasswordVisible by remember { mutableStateOf(false) }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showReauthSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive
    val errorColor = colors.error

    // Password Validation Rules
    val isMinLengthMet = newPassword.length >= 8
    val isLetterAndNumberMet = newPassword.any { it.isLetter() } && newPassword.any { it.isDigit() }
    val isMatch = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword == confirmPassword
    val isMismatchError = confirmPassword.isNotEmpty() && newPassword != confirmPassword

    val canSave = if (hasExistingPassword) {
        oldPassword.isNotBlank() && isMinLengthMet && isLetterAndNumberMet && isMatch
    } else {
        isMinLengthMet && isLetterAndNumberMet && isMatch
    }

    val handleSavePassword = {
        if (canSave) {
            errorMessage = null
            val user = auth.currentUser
            if (user != null) {
                if (hasExistingPassword) {
                    // Re-authenticate with current password then update
                    val credential = EmailAuthProvider.getCredential(user.email ?: userEmail, oldPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    onPasswordSavedSuccessfully(false)
                                }
                                .addOnFailureListener { e ->
                                    if (e is FirebaseAuthRecentLoginRequiredException) {
                                        showReauthSheet = true
                                    } else {
                                        errorMessage = e.localizedMessage ?: "Failed to update password"
                                    }
                                }
                        }
                        .addOnFailureListener {
                            errorMessage = "Current password is wrong"
                        }
                } else {
                    // Link Email/Password credential to Google account
                    val credential = EmailAuthProvider.getCredential(user.email ?: userEmail, newPassword)
                    user.linkWithCredential(credential)
                        .addOnSuccessListener {
                            onPasswordSavedSuccessfully(true)
                        }
                        .addOnFailureListener { e ->
                            if (e is FirebaseAuthRecentLoginRequiredException) {
                                showReauthSheet = true
                            } else {
                                // Try updating directly if already has provider
                                user.updatePassword(newPassword)
                                    .addOnSuccessListener {
                                        onPasswordSavedSuccessfully(true)
                                    }
                                    .addOnFailureListener { err ->
                                        errorMessage = err.localizedMessage ?: "Failed to set password"
                                    }
                            }
                        }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (hasExistingPassword) "Change password" else "Set password",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                // Save Action
                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) tealAccent else tabInactiveColor,
                    modifier = Modifier
                        .clickable(enabled = canSave) { handleSavePassword() }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Password Fields Card
            SettingsCard {
                if (!hasExistingPassword) {
                    // Row 1: Email (Locked)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Email",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userEmail.ifBlank { auth.currentUser?.email ?: "Account Email" },
                                fontSize = 16.sp,
                                color = primaryTextColor.copy(alpha = 0.50f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Locked email",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                } else {
                    // Row 1: Current Password
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current password",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            BasicTextField(
                                value = oldPassword,
                                onValueChange = {
                                    oldPassword = it
                                    errorMessage = null
                                },
                                singleLine = true,
                                visualTransformation = if (isOldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(tealAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        IconButton(
                            onClick = { isOldPasswordVisible = !isOldPasswordVisible },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isOldPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                }

                // Row 2: New Password
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "New password",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            singleLine = true,
                            visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                color = primaryTextColor
                            ),
                            cursorBrush = SolidColor(tealAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    IconButton(
                        onClick = { isNewPasswordVisible = !isNewPasswordVisible },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 3: Confirm Password
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Confirm password",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            singleLine = true,
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                handleSavePassword()
                            }),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                color = primaryTextColor
                            ),
                            cursorBrush = SolidColor(tealAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    IconButton(
                        onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Forgot password link (Change Password mode only)
            if (hasExistingPassword) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Forgot password?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tealAccent,
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .clickable {
                            val emailToReset = userEmail.ifBlank { auth.currentUser?.email ?: "" }
                            if (emailToReset.isNotBlank()) {
                                auth.sendPasswordResetEmail(emailToReset)
                                    .addOnSuccessListener {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Reset link sent to $emailToReset")
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(e.localizedMessage ?: "Failed to send reset link")
                                        }
                                    }
                            }
                        }
                )
            }

            // Error Message Line
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage!!,
                    fontSize = 13.sp,
                    color = errorColor,
                    modifier = Modifier.padding(start = 24.dp)
                )
            } else if (isMismatchError) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Passwords do not match",
                    fontSize = 13.sp,
                    color = errorColor,
                    modifier = Modifier.padding(start = 24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two Rules Section
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Rule 1: At least 8 characters
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isMinLengthMet) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Rule met",
                            tint = tealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = "Rule unmet",
                            tint = tabInactiveColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "At least 8 characters",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isMinLengthMet) primaryTextColor else secondaryTextColor
                    )
                }

                // Rule 2: A letter and a number
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLetterAndNumberMet) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Rule met",
                            tint = tealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = "Rule unmet",
                            tint = tabInactiveColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "A letter and a number",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isLetterAndNumberMet) primaryTextColor else secondaryTextColor
                    )
                }
            }
        }
    }

    if (showReauthSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReauthSheet = false },
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
                    text = "Confirm it’s you",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "For your security, sign in with Google again to set a password.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor,
                        modifier = Modifier
                            .clickable { showReauthSheet = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(28.dp))

                    Text(
                        text = "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                showReauthSheet = false
                                onGoogleReauthRequest()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
