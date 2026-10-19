package com.fabxdi.dibadge.ui.auth

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.maskEmail
import com.fabxdi.dibadge.viewmodel.AuthUiState
import com.fabxdi.dibadge.viewmodel.AuthViewModel
import com.fabxdi.dibadge.viewmodel.SignupVerificationState
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

const val TERMS_URL = "https://example.com/terms"

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit = {},
    onBackToWelcome: (() -> Unit)? = null
) {
    if (onBackToWelcome != null) {
        BackHandler {
            onBackToWelcome()
        }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val credentialManager = remember { CredentialManager.create(context) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val isDarkTheme = colors.background.red < 0.5f

    // System Bar Configuration
    val window = (context as? ComponentActivity)?.window
    SideEffect {
        window?.let { win ->
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val signupVerifyState by viewModel.signupVerifyState.collectAsState()

    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser) {
        if (currentUser != null && !signupVerifyState.isOpen) {
            onAuthSuccess()
        }
    }

    val isLoading = uiState is AuthUiState.Loading

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tabInactiveColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    fun triggerGoogleSignIn() {
        coroutineScope.launch {
            try {
                val webClientId = try {
                    val idRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    if (idRes != 0) context.getString(idRes) else "390433769514-upbvdbac943d86u5o39b9uon2l2nbdle.apps.googleusercontent.com"
                } catch (e: Exception) {
                    "390433769514-upbvdbac943d86u5o39b9uon2l2nbdle.apps.googleusercontent.com"
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.googleSignIn(googleIdTokenCredential.idToken)
                } else {
                    viewModel.googleSignInError("Google Sign-In failed to retrieve token")
                }
            } catch (e: GetCredentialCancellationException) {
                // User cancelled - do nothing
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Google Sign-In error"
                if (!msg.contains("cancel", ignoreCase = true)) {
                    viewModel.googleSignInError(msg)
                }
            }
        }
    }

    val performSignupValidationAndCheck = {
        focusManager.clearFocus()
        val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches().also { if (!it) emailError = "Enter a valid email address" }
        val isPassValid = (password.length >= 8).also { if (!it) passwordError = "Password must be at least 8 characters" }
        val isMatch = (confirmPassword == password).also { if (!it) confirmPasswordError = "Passwords do not match" }
        if (isEmailValid && isPassValid && isMatch) {
            viewModel.checkEmailAndRequestSignupCode(
                email = email,
                onRegistered = {
                    emailError = "This email is already registered. Log in or use Google."
                },
                onCodeRequested = {},
                onError = { msg ->
                    if (msg.contains("registered", ignoreCase = true)) {
                        emailError = "This email is already registered. Log in or use Google."
                    } else {
                        snackbarMessage = msg
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBgColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Brand Row: Logo Mark + "diBadge" side-by-side (Centered in 240dp top band)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.semantics(mergeDescendants = true) {}
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_dibadge_mark),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(width = 34.dp, height = 41.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "diBadge",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp,
                            color = primaryTextColor
                        )
                    }
                }

                // 2. Form Block (Starts at 240dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isLoading) 0.5f else 1.0f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSignUpMode) {
                        // --- SIGN UP FORM ---
                        // Email Tile
                        LoginTileField(
                            value = email,
                            onValueChange = {
                                email = it
                                emailError = null
                            },
                            label = "Email address",
                            errorMessage = emailError,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Tile
                        LoginTileField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                            },
                            label = "Password",
                            errorMessage = passwordError,
                            enabled = !isLoading,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onPasswordToggle = { passwordVisible = !passwordVisible },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Confirm Password Tile
                        LoginTileField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                confirmPasswordError = null
                            },
                            label = "Confirm password",
                            errorMessage = confirmPasswordError,
                            enabled = !isLoading,
                            isPassword = true,
                            passwordVisible = confirmPasswordVisible,
                            onPasswordToggle = { confirmPasswordVisible = !confirmPasswordVisible },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { performSignupValidationAndCheck() }
                            )
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Sign up Button
                        Button(
                            onClick = { performSignupValidationAndCheck() },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryTextColor,
                                contentColor = pageBgColor,
                                disabledContainerColor = primaryTextColor.copy(alpha = 0.38f),
                                disabledContentColor = pageBgColor.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = pageBgColor,
                                    strokeWidth = 2.6.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "Sign up",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = pageBgColor
                                )
                            }
                        }
                    } else {
                        // --- LOGIN FORM ---
                        // Email Tile
                        LoginTileField(
                            value = email,
                            onValueChange = {
                                email = it
                                emailError = null
                            },
                            label = "Email address",
                            errorMessage = emailError,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Tile
                        LoginTileField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                            },
                            label = "Password",
                            errorMessage = passwordError,
                            enabled = !isLoading,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onPasswordToggle = { passwordVisible = !passwordVisible },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches().also { if (!it) emailError = "Enter a valid email address" }
                                    val isPassValid = (password.length >= 8).also { if (!it) passwordError = "Password must be at least 8 characters" }
                                    if (isEmailValid && isPassValid) {
                                        viewModel.login(email, password)
                                    }
                                }
                            )
                        )

                        // "Forgot password?" Link
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "Forgot password?",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = secondaryTextColor,
                                modifier = Modifier.clickable(enabled = !isLoading) { showForgotPasswordDialog = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Login Button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches().also { if (!it) emailError = "Enter a valid email address" }
                                val isPassValid = (password.length >= 8).also { if (!it) passwordError = "Password must be at least 8 characters" }
                                if (isEmailValid && isPassValid) {
                                    viewModel.login(email, password)
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryTextColor,
                                contentColor = pageBgColor,
                                disabledContainerColor = primaryTextColor.copy(alpha = 0.38f),
                                disabledContentColor = pageBgColor.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = pageBgColor,
                                    strokeWidth = 2.6.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "Login",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = pageBgColor
                                )
                            }
                        }
                    }

                    // Global Auth Error Message
                    if (uiState is AuthUiState.Error) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = (uiState as AuthUiState.Error).message,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = errorColor
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // "Or continue with" Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = outlineColor
                        )
                        Text(
                            text = "Or continue with",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = outlineColor
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Google Tile
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(enabled = !isLoading) { triggerGoogleSignIn() },
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // 3. Bottom Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp, start = 24.dp, end = 24.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Two-Tone Link
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .alpha(if (isLoading) 0.5f else 1.0f)
                        .clickable(enabled = !isLoading) {
                            isSignUpMode = !isSignUpMode
                            emailError = null
                            passwordError = null
                            confirmPasswordError = null
                            viewModel.clearError()
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {}
                    ) {
                        Text(
                            text = if (isSignUpMode) "Have an account?" else "New here?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSignUpMode) "Login" else "Sign up",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = tealAccent
                        )
                    }
                }

                // Right Link ("Terms of Use")
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .alpha(if (isLoading) 0.5f else 1.0f)
                        .clickable(enabled = !isLoading) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Terms of Use",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        textDecoration = TextDecoration.Underline,
                        color = tabInactiveColor
                    )
                }
            }
        }
    }

    // Signup Verification Code Sheet
    if (signupVerifyState.isOpen) {
        SignupVerificationSheet(
            email = signupVerifyState.email,
            verificationState = signupVerifyState,
            onTypedCodeChange = { viewModel.updateSignupTypedCode(it) },
            onVerifyClick = {
                viewModel.verifySignupCode(
                    onSuccess = {
                        if (!signupVerifyState.isGoogleSignup) {
                            viewModel.signUp("", email, password, confirmPassword)
                        }
                    }
                )
            },
            onResendClick = {
                viewModel.resendSignupCode {
                    snackbarMessage = "New code sent"
                }
            },
            onDismiss = {
                val wasGoogle = signupVerifyState.isGoogleSignup
                viewModel.dismissSignupVerifySheet {
                    if (wasGoogle) {
                        snackbarMessage = "Sign up cancelled"
                    }
                }
            }
        )
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetMessage by remember { mutableStateOf<String?>(null) }
        var isResetSuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = "Reset Password",
                    style = MaterialTheme.typography.titleLarge,
                    color = primaryTextColor
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your email address to receive a password reset link.",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resetMessage!!,
                            fontSize = 12.sp,
                            color = if (isResetSuccess) tealAccent else errorColor
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendPasswordResetEmail(resetEmail) { success, msg ->
                            isResetSuccess = success
                            resetMessage = msg
                        }
                    }
                ) {
                    Text("Send reset link", fontWeight = FontWeight.Bold, color = tealAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel", color = primaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupVerificationSheet(
    email: String,
    verificationState: SignupVerificationState,
    onTypedCodeChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onResendClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val typedCode = verificationState.typedCode
    val maskedEmail = remember(email) { maskEmail(email) }
    val focusRequester = remember { FocusRequester() }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val wellBgColor = diBadgeColors.well
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

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
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Verify your email",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            val bodyText = if (verificationState.isGoogleSignup) {
                "We sent a 6-digit code to $maskedEmail. Enter it to finish signing up."
            } else {
                "We sent a 6-digit code to $maskedEmail. Enter it to create your account."
            }

            Text(
                text = bodyText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = typedCode,
                    onValueChange = { newValue ->
                        if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                            onTypedCodeChange(newValue)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .size(1.dp)
                        .alpha(0f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasError = verificationState.errorMessage != null
                    for (i in 0 until 6) {
                        val digit = typedCode.getOrNull(i)?.toString() ?: ""
                        val isActive = typedCode.length == i && !verificationState.isLocked

                        val borderColor = when {
                            hasError -> errorColor
                            isActive -> primaryTextColor
                            else -> colors.outline
                        }

                        Surface(
                            modifier = Modifier
                                .size(width = 46.dp, height = 56.dp)
                                .clickable { focusRequester.requestFocus() },
                            shape = RoundedCornerShape(12.dp),
                            color = wellBgColor,
                            border = BorderStroke(if (isActive || hasError) 1.5.dp else 1.dp, borderColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                            }
                        }
                    }
                }
            }

            if (verificationState.errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = verificationState.errorMessage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = errorColor,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val secondsLeft = verificationState.resendSecondsLeft
                val canResend = secondsLeft == 0 && !verificationState.isLocked
                if (canResend) {
                    Text(
                        text = "Resend code",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier.clickable(onClick = onResendClick)
                    )
                } else {
                    val timerStr = String.format(Locale.getDefault(), "0:%02d", secondsLeft)
                    Text(
                        text = "Resend code in $timerStr",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )

                    val isVerifyEnabled = typedCode.length == 6 &&
                            !verificationState.isVerifying &&
                            !verificationState.isLocked

                    TextButton(
                        onClick = onVerifyClick,
                        enabled = isVerifyEnabled,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = tealAccent,
                            disabledContentColor = tealAccent.copy(alpha = 0.45f)
                        )
                    ) {
                        if (verificationState.isVerifying) {
                            CircularProgressIndicator(
                                color = tealAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "Verify",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun LoginTileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val cardBgColor = colors.surface
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val accentColor = diBadgeColors.accent
    val errorColor = colors.error

    val hasTextOrFocus = value.isNotEmpty() || isFocused

    val borderColor = when {
        errorMessage != null -> errorColor
        isFocused -> primaryTextColor
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1.0f else 0.5f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            shape = RoundedCornerShape(14.dp),
            color = cardBgColor,
            border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = if (hasTextOrFocus) 12.sp else 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isFocused) primaryTextColor else secondaryTextColor
                        )

                        if (hasTextOrFocus) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            enabled = enabled,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor,
                                letterSpacing = if (isPassword && !passwordVisible) 1.sp else TextUnit.Unspecified
                            ),
                            cursorBrush = SolidColor(accentColor),
                            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = keyboardOptions,
                            keyboardActions = keyboardActions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                        )
                    }

                    if (isPassword) {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 44.dp)
                                .clickable(enabled = enabled) { onPasswordToggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = errorColor,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics {
                        error(errorMessage)
                    }
            )
        }
    }
}
