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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.viewmodel.AuthUiState
import com.fabxdi.dibadge.viewmodel.AuthViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

// Color Tokens
val AuthBackgroundColor = Color(0xFF262624)
val AuthTextColor = Color(0xFFFAF9F5)
val AuthHintColor = Color(0xFFA8A69E)
val AuthFieldLineColor = Color(0xFF5A5955)
val AuthDividerLineColor = Color(0xFF3A3A37)
val AuthForgotLinkColor = Color(0xFFD6D4CC)
val AuthAccentTeal = Color(0xFF14B8A6)
val AuthErrorColor = Color(0xFFFF8A80)

// TODO: Replace with the actual Terms of Use URL
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

    // System Bar Configuration (Light icons, dark background)
    val window = (context as? ComponentActivity)?.window
    SideEffect {
        window?.let { win ->
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

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

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onAuthSuccess()
        }
    }

    val isLoading = uiState is AuthUiState.Loading

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
                // User cancelled - do nothing and show no error
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Google Sign-In error"
                if (!msg.contains("cancel", ignoreCase = true)) {
                    viewModel.googleSignInError(msg)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // 1. Top Brand Row: Icon Mark + "diBadge" side-by-side, 72dp from top below status bar inset
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
            contentAlignment = Alignment.TopCenter
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
                    modifier = Modifier.size(width = 27.dp, height = 36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "diBadge",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    color = AuthTextColor
                )
            }
        }

        // 2. Form Block: Vertically centered in the free space between logo and bottom row
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 115.dp, bottom = 75.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSignUpMode) {
                    // --- SIGN UP FORM ---
                    // Email Field
                    UnderlineTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = null
                        },
                        hint = "Email address",
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Password Field
                    UnderlineTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        hint = "Password",
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Confirm Password Field
                    UnderlineTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            confirmPasswordError = null
                        },
                        hint = "Confirm password",
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
                            onDone = {
                                focusManager.clearFocus()
                                val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches().also { if (!it) emailError = "Enter a valid email address" }
                                val isPassValid = (password.length >= 8).also { if (!it) passwordError = "Password must be at least 8 characters" }
                                val isMatch = (confirmPassword == password).also { if (!it) confirmPasswordError = "Passwords do not match" }
                                if (isEmailValid && isPassValid && isMatch) {
                                    viewModel.signUp("", email, password, confirmPassword)
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // "Sign up" Outlined Pill Button
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches().also { if (!it) emailError = "Enter a valid email address" }
                            val isPassValid = (password.length >= 8).also { if (!it) passwordError = "Password must be at least 8 characters" }
                            val isMatch = (confirmPassword == password).also { if (!it) confirmPasswordError = "Passwords do not match" }
                            if (isEmailValid && isPassValid && isMatch) {
                                viewModel.signUp("", email, password, confirmPassword)
                            }
                        },
                        enabled = !isLoading,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, if (isLoading) AuthAccentTeal.copy(alpha = 0.38f) else AuthAccentTeal),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = AuthAccentTeal,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = AuthAccentTeal.copy(alpha = 0.38f)
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = AuthAccentTeal,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "Sign up",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuthAccentTeal
                            )
                        }
                    }
                } else {
                    // --- LOGIN FORM ---
                    // Email Field
                    UnderlineTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = null
                        },
                        hint = "Email address",
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

                    Spacer(modifier = Modifier.height(28.dp))

                    // Password Field
                    UnderlineTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        hint = "Password",
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

                    // "Forgot password?" Link (Medium 14sp #D6D4CC, right-aligned)
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
                            color = AuthForgotLinkColor,
                            modifier = Modifier
                                .alpha(if (isLoading) 0.38f else 1.0f)
                                .clickable(enabled = !isLoading) { showForgotPasswordDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // "Login" Outlined Pill Button
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches().also { if (!it) emailError = "Enter a valid email address" }
                            val isPassValid = (password.length >= 8).also { if (!it) passwordError = "Password must be at least 8 characters" }
                            if (isEmailValid && isPassValid) {
                                viewModel.login(email, password)
                            }
                        },
                        enabled = !isLoading,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, if (isLoading) AuthAccentTeal.copy(alpha = 0.38f) else AuthAccentTeal),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = AuthAccentTeal,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = AuthAccentTeal.copy(alpha = 0.38f)
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = AuthAccentTeal,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "Login",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuthAccentTeal
                            )
                        }
                    }
                }

                // Global Auth Exception Error Message
                if (uiState is AuthUiState.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = (uiState as AuthUiState.Error).message,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = AuthErrorColor
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // "Or Continue With" Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = AuthDividerLineColor
                    )
                    Text(
                        text = "Or Continue With",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = AuthHintColor,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = AuthDividerLineColor
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Google Sign-In Button (Official Logo + Label "Google", Medium 16sp #FAF9F5, 48dp high)
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .alpha(if (isLoading) 0.38f else 1.0f)
                        .clickable(enabled = !isLoading) { triggerGoogleSignIn() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AuthTextColor
                        )
                    }
                }
            }
        }

        // 3. Bottom Row: 28dp above bottom edge (plus navigation inset)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Two-Tone Bottom Link ("New here? Sign up" or "Have an account? Login")
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .alpha(if (isLoading) 0.38f else 1.0f)
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
                            color = AuthHintColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSignUpMode) "Login" else "Sign up",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuthAccentTeal
                        )
                    }
                }

                // Right Link ("Terms of Use", Regular 13sp, underlined #A8A69E)
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .alpha(if (isLoading) 0.38f else 1.0f)
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
                        color = AuthHintColor
                    )
                }
            }
        }
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your email address to receive a password reset link.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = if (isResetSuccess) AuthAccentTeal else AuthErrorColor
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
                    Text("Send reset link", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    errorMessage: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isFocused by remember { mutableStateOf(false) }
    var hasBeenEdited by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1.0f else 0.38f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = {
                    hasBeenEdited = true
                    onValueChange(it)
                },
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = AuthTextColor
                ),
                cursorBrush = SolidColor(AuthAccentTeal),
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = hint,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = AuthHintColor
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Password Eye Toggle
            if (isPassword) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 52.dp)
                        .align(Alignment.CenterEnd)
                        .clickable(enabled = enabled) { onPasswordToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = AuthHintColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Underline Indicator (1dp #5A5955 -> 2dp #14B8A6 when focused -> 2dp #FF8A80 when error)
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = if (errorMessage != null || isFocused) 2.dp else 1.dp,
            color = when {
                errorMessage != null -> AuthErrorColor
                isFocused -> AuthAccentTeal
                else -> AuthFieldLineColor
            }
        )

        // Inline Error Message (Regular 12sp #FF8A80, with TalkBack error semantics)
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = AuthErrorColor,
                modifier = Modifier.semantics {
                    error(errorMessage)
                }
            )
        }
    }
}
