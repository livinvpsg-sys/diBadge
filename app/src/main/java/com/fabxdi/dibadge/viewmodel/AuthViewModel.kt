package com.fabxdi.dibadge.viewmodel

import com.fabxdi.dibadge.util.OtpApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: FirebaseUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

data class SignupVerificationState(
    val isOpen: Boolean = false,
    val email: String = "",
    val challengeId: String = "",
    val code: String = "",
    val typedCode: String = "",
    val isVerifying: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val triesLeft: Int = 5,
    val isLocked: Boolean = false,
    val isGoogleSignup: Boolean = false,
    /** True when the code is for logging in to an existing account (not for creating one). */
    val isLogin: Boolean = false,
    val resendSecondsLeft: Int = 60
)

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _signupVerifyState = MutableStateFlow(SignupVerificationState())

    // True while a new Google account still has to choose its country before Home opens
    private val _needsCountry = MutableStateFlow(false)
    val needsCountry: StateFlow<Boolean> = _needsCountry.asStateFlow()
    val signupVerifyState: StateFlow<SignupVerificationState> = _signupVerifyState.asStateFlow()

    private var resendTimerJob: Job? = null

    // While a login or a new Google sign-up waits for its email code, the app must not open Home.
    @Volatile private var otpHold = false

    // Kept in memory only, while the login code is being entered. Nothing is signed in during this time,
    // so closing the app now leaves the person signed out.
    private var pendingEmail: String = ""
    private var pendingPassword: String = ""
    private var pendingGoogleCredential: AuthCredential? = null

    private fun clearPendingLogin() {
        pendingEmail = ""
        pendingPassword = ""
        pendingGoogleCredential = null
    }

    private fun releaseHold() {
        otpHold = false
        _currentUser.value = auth.currentUser
        // The auth listener skipped this while the code was pending, so give the account its profile record now
        auth.currentUser?.let { ensureProfileRecord(it) }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val signedIn = firebaseAuth.currentUser
        _currentUser.value = if (otpHold) null else signedIn
        // Every signed-in account gets its profile record, so others can find it in search.
        // A new Google account waits until its email code is confirmed.
        if (signedIn != null && !otpHold && !_signupVerifyState.value.isOpen) ensureProfileRecord(signedIn)
    }

    private fun ensureProfileRecord(user: FirebaseUser) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ensureUserProfileDoc(user)
            } catch (e: Exception) {}
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter email and password")
            return
        }
        _uiState.value = AuthUiState.Loading
        otpHold = true
        auth.signInWithEmailAndPassword(email.trim(), pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val codeEmail = (user?.email ?: email).trim()
                    if (user != null) {
                        // The password is right. Sign out again and only sign in for real after the email code.
                        auth.signOut()
                        pendingEmail = email.trim()
                        pendingPassword = pass
                        pendingGoogleCredential = null
                        requestSignupCodeInternal(
                            email = codeEmail,
                            isGoogle = false,
                            isLogin = true,
                            onSuccess = {},
                            onError = { msg ->
                                clearPendingLogin()
                                otpHold = false
                                _uiState.value = AuthUiState.Error(msg)
                            }
                        )
                    } else {
                        otpHold = false
                        _uiState.value = AuthUiState.Error("Login failed")
                    }
                } else {
                    otpHold = false
                    val msg = mapFirebaseError(task.exception?.message)
                    _uiState.value = AuthUiState.Error(msg)
                }
            }
    }

    /** Signs in for real once the login code has been confirmed. */
    private fun completeLogin() {
        _uiState.value = AuthUiState.Loading
        val credential = pendingGoogleCredential
        val task = if (credential != null) {
            auth.signInWithCredential(credential)
        } else {
            auth.signInWithEmailAndPassword(pendingEmail, pendingPassword)
        }
        task.addOnCompleteListener { t ->
            clearPendingLogin()
            if (t.isSuccessful && auth.currentUser != null) {
                releaseHold()
                _uiState.value = AuthUiState.Success(auth.currentUser!!)
            } else {
                otpHold = false
                _uiState.value = AuthUiState.Error(mapFirebaseError(t.exception?.message))
            }
        }
    }

    fun checkEmailAndRequestSignupCode(
        email: String,
        onRegistered: () -> Unit,
        onCodeRequested: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) {
            onError("Enter a valid email address")
            return
        }

        _uiState.value = AuthUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val methods = auth.fetchSignInMethodsForEmail(cleanEmail).await().signInMethods
                if (!methods.isNullOrEmpty()) {
                    _uiState.value = AuthUiState.Idle
                    onRegistered()
                } else {
                    requestSignupCodeInternal(
                        email = cleanEmail,
                        isGoogle = false,
                        onSuccess = onCodeRequested,
                        onError = onError
                    )
                }
            } catch (e: Exception) {
                // If fetchSignInMethods is restricted or offline, proceed to send verification code
                requestSignupCodeInternal(
                        email = cleanEmail,
                        isGoogle = false,
                        onSuccess = onCodeRequested,
                        onError = onError
                    )
            }
        }
    }

    private fun requestSignupCodeInternal(
        email: String,
        isGoogle: Boolean,
        isLogin: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // The server makes the code and emails it. The app never sees the code.
            when (val result = OtpApi.send(email)) {
                is OtpApi.SendResult.Sent -> {
                    _signupVerifyState.value = SignupVerificationState(
                        isOpen = true,
                        email = email,
                        typedCode = "",
                        isGoogleSignup = isGoogle,
                        isLogin = isLogin,
                        resendSecondsLeft = 60
                    )
                    startResendTimer()
                    _uiState.value = AuthUiState.Idle
                    onSuccess()
                }
                is OtpApi.SendResult.Failed -> {
                    _uiState.value = AuthUiState.Idle
                    onError(result.message)
                }
            }
        }
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            for (sec in 60 downTo 0) {
                _signupVerifyState.value = _signupVerifyState.value.copy(resendSecondsLeft = sec)
                delay(1000)
            }
        }
    }

    fun updateSignupTypedCode(typed: String) {
        val current = _signupVerifyState.value
        _signupVerifyState.value = current.copy(
            typedCode = typed,
            errorMessage = if (current.errorMessage?.contains("not right") == true) null else current.errorMessage
        )
    }

    fun verifySignupCode(onSuccess: () -> Unit) {
        val state = _signupVerifyState.value
        if (state.typedCode.length != 6 || state.isVerifying || state.isLocked) return

        _signupVerifyState.value = state.copy(isVerifying = true)

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = OtpApi.verify(state.email, state.typedCode)) {
                is OtpApi.VerifyResult.Ok -> {
                    _signupVerifyState.value = SignupVerificationState(isOpen = false)
                    when {
                        state.isLogin -> completeLogin()
                        state.isGoogleSignup -> {
                            // A new Google account must now choose its country; Home opens after that
                            _needsCountry.value = true
                        }
                        else -> onSuccess()
                    }
                }
                is OtpApi.VerifyResult.Wrong -> {
                    val left = result.triesLeft
                    val triesMsg = if (left == 1) "1 try left." else "$left tries left."
                    _signupVerifyState.value = _signupVerifyState.value.copy(
                        isVerifying = false,
                        triesLeft = left,
                        errorMessage = "That code is not right. $triesMsg"
                    )
                }
                is OtpApi.VerifyResult.Locked -> {
                    _signupVerifyState.value = _signupVerifyState.value.copy(
                        isVerifying = false,
                        triesLeft = 0,
                        isLocked = true,
                        errorMessage = "Too many wrong codes. Try again in 10 minutes."
                    )
                }
                is OtpApi.VerifyResult.Expired -> {
                    _signupVerifyState.value = _signupVerifyState.value.copy(
                        isVerifying = false,
                        errorMessage = "That code has expired. Tap Resend to get a new one."
                    )
                }
                is OtpApi.VerifyResult.Failed -> {
                    _signupVerifyState.value = _signupVerifyState.value.copy(
                        isVerifying = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun resendSignupCode(onSent: () -> Unit) {
        val state = _signupVerifyState.value
        val email = state.email
        if (email.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = OtpApi.send(email)) {
                is OtpApi.SendResult.Sent -> {
                    _signupVerifyState.value = _signupVerifyState.value.copy(
                        typedCode = "",
                        errorMessage = null,
                        triesLeft = 5,
                        isLocked = false,
                        resendSecondsLeft = 60
                    )
                    startResendTimer()
                    onSent()
                }
                is OtpApi.SendResult.Failed -> {
                    _signupVerifyState.value = _signupVerifyState.value.copy(errorMessage = result.message)
                }
            }
        }
    }

    fun dismissSignupVerifySheet(onCancelled: () -> Unit = {}) {
        val state = _signupVerifyState.value
        resendTimerJob?.cancel()

        if (state.isGoogleSignup && !state.isLogin) {
            val user = auth.currentUser
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    user?.delete()?.await()
                } catch (e: Exception) {}
                auth.signOut()
            }
        }

        if (state.isLogin) clearPendingLogin()
        otpHold = false
        _currentUser.value = auth.currentUser
        _uiState.value = AuthUiState.Idle
        _signupVerifyState.value = SignupVerificationState(isOpen = false)
        onCancelled()
    }

    /** Saves the country chosen by a new Google account, then lets it into the app. */
    fun setGoogleCountry(isoCode: String) {
        if (!_needsCountry.value) return
        val user = auth.currentUser ?: return
        val iso = isoCode.trim().uppercase()
        if (iso.isBlank()) return
        // Switch off first: the picker also calls its dismiss handler right after a choice
        _needsCountry.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(user.uid)
                    .set(mapOf("country" to iso), SetOptions.merge())
                    .await()
                releaseHold()
            } catch (e: Exception) {
                _needsCountry.value = true
                _uiState.value = AuthUiState.Error("Couldn't save your country. Check your connection and try again.")
            }
        }
    }

    /** Closing the country step cancels the new Google sign-up, like closing the code step does. */
    fun cancelGoogleSignup(onCancelled: () -> Unit = {}) {
        if (!_needsCountry.value) return
        _needsCountry.value = false
        val user = auth.currentUser
        viewModelScope.launch(Dispatchers.IO) {
            try {
                user?.delete()?.await()
            } catch (e: Exception) {}
            auth.signOut()
            otpHold = false
            _currentUser.value = auth.currentUser
        }
        onCancelled()
    }

    fun signUp(name: String, email: String, pass: String, confirmPass: String, country: String) {
        if (country.isBlank()) {
            _uiState.value = AuthUiState.Error("Select your country")
            return
        }
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all required fields")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("Passwords do not match")
            return
        }
        if (pass.length < 8) {
            _uiState.value = AuthUiState.Error("Password must be at least 8 characters")
            return
        }

        _uiState.value = AuthUiState.Loading
        auth.createUserWithEmailAndPassword(email.trim(), pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        if (name.isNotBlank()) {
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(name.trim())
                                .build()
                            user.updateProfile(profileUpdates)
                        }
                        // Create initial user document in Firestore with emailOtp: true
                        val signUpFields = mutableMapOf<String, Any>(
                            "email" to (user.email ?: email.trim()),
                            "emailLower" to (user.email ?: email.trim()).trim().lowercase(),
                            "emailOtp" to true,
                            "country" to country.trim().uppercase(),
                            "createdAt" to System.currentTimeMillis()
                        )
                        if (name.isNotBlank()) {
                            signUpFields["displayName"] = name.trim()
                            signUpFields["displayNameLower"] = name.trim().lowercase()
                        }
                        firestore.collection("users").document(user.uid).set(signUpFields, SetOptions.merge())
                        _uiState.value = AuthUiState.Success(user)
                    } else {
                        _uiState.value = AuthUiState.Error("Account created successfully")
                    }
                } else {
                    val msg = mapFirebaseError(task.exception?.message)
                    _uiState.value = AuthUiState.Error(msg)
                }
            }
    }

    fun googleSignIn(idToken: String) {
        _uiState.value = AuthUiState.Loading
        otpHold = true
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser == true
                    if (user != null) {
                        // Keep the lower-case email on the user record so exact-email search finds Google accounts too.
                        user.email?.takeIf { it.isNotBlank() }?.let { em ->
                            firestore.collection("users").document(user.uid).set(
                                mapOf("email" to em, "emailLower" to em.trim().lowercase()),
                                SetOptions.merge()
                            )
                        }
                        val codeEmail = user.email
                        if (codeEmail.isNullOrBlank()) {
                            // No email to send a code to
                            otpHold = false
                            _currentUser.value = auth.currentUser
                            _uiState.value = AuthUiState.Success(user)
                        } else if (isNewUser) {
                            // New account: stays signed in so a cancelled sign-up can delete it
                            requestSignupCodeInternal(
                                email = codeEmail,
                                isGoogle = true,
                                onSuccess = {},
                                onError = { msg ->
                                    otpHold = false
                                    _uiState.value = AuthUiState.Error(msg)
                                }
                            )
                        } else {
                            // Existing account: sign out again and only sign in for real after the email code
                            auth.signOut()
                            pendingGoogleCredential = credential
                            pendingEmail = ""
                            pendingPassword = ""
                            requestSignupCodeInternal(
                                email = codeEmail,
                                isGoogle = true,
                                isLogin = true,
                                onSuccess = {},
                                onError = { msg ->
                                    clearPendingLogin()
                                    otpHold = false
                                    _uiState.value = AuthUiState.Error(msg)
                                }
                            )
                        }
                    } else {
                        otpHold = false
                        _uiState.value = AuthUiState.Error("Google Sign-In failed")
                    }
                } else {
                    otpHold = false
                    val msg = mapFirebaseError(task.exception?.message)
                    _uiState.value = AuthUiState.Error(msg)
                }
            }
    }

    fun googleSignInError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank()) {
            onResult(false, "Please enter your email address")
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, "Reset link sent to $email")
                } else {
                    val msg = mapFirebaseError(task.exception?.message)
                    onResult(false, msg)
                }
            }
    }

    private fun mapFirebaseError(rawMsg: String?): String {
        if (rawMsg.isNullOrBlank()) return "An error occurred. Please try again."
        val lower = rawMsg.lowercase()
        return when {
            lower.contains("user-not-found") || lower.contains("no user record") -> "No account found with this email"
            lower.contains("wrong-password") || lower.contains("invalid-credential") -> "Invalid email or password"
            lower.contains("email-already-in-use") -> "This email is already registered. Log in or use Google."
            lower.contains("weak-password") -> "Password is too weak"
            lower.contains("network") || lower.contains("connection") -> "Network error. Please check your connection."
            lower.contains("too-many-requests") -> "Too many attempts. Please try again later."
            else -> rawMsg
        }
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = AuthUiState.Idle
    }

    fun clearError() {
        _uiState.value = AuthUiState.Idle
    }
}
