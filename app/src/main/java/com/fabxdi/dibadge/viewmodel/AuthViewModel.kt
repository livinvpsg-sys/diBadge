package com.fabxdi.dibadge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
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
    val signupVerifyState: StateFlow<SignupVerificationState> = _signupVerifyState.asStateFlow()

    private var resendTimerJob: Job? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUser.value = firebaseAuth.currentUser
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
        auth.signInWithEmailAndPassword(email.trim(), pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        _uiState.value = AuthUiState.Success(user)
                    } else {
                        _uiState.value = AuthUiState.Error("Login failed")
                    }
                } else {
                    val msg = mapFirebaseError(task.exception?.message)
                    _uiState.value = AuthUiState.Error(msg)
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
                    requestSignupCodeInternal(cleanEmail, isGoogle = false, onCodeRequested, onError)
                }
            } catch (e: Exception) {
                // If fetchSignInMethods is restricted or offline, proceed to send verification code
                requestSignupCodeInternal(cleanEmail, isGoogle = false, onCodeRequested, onError)
            }
        }
    }

    private fun requestSignupCodeInternal(
        email: String,
        isGoogle: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val generatedCode = String.format("%06d", (100000..999999).random())
        val challengeId = UUID.randomUUID().toString()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val challengeData = mapOf(
                    "challengeId" to challengeId,
                    "email" to email,
                    "code" to generatedCode,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to System.currentTimeMillis() + 10 * 60 * 1000,
                    "attempts" to 0
                )

                firestore.collection("signupChallenges")
                    .document(challengeId)
                    .set(challengeData)
                    .await()

                try {
                    firestore.collection("mail").add(mapOf(
                        "to" to email,
                        "message" to mapOf(
                            "subject" to "Your diBadge verification code",
                            "text" to "Your diBadge verification code is $generatedCode. It expires in 10 minutes. If you did not try to create an account, you can ignore this email."
                        )
                    ))
                } catch (e: Exception) {}

                _signupVerifyState.value = SignupVerificationState(
                    isOpen = true,
                    email = email,
                    challengeId = challengeId,
                    code = generatedCode,
                    typedCode = "",
                    isGoogleSignup = isGoogle,
                    resendSecondsLeft = 60
                )

                startResendTimer()
                _uiState.value = AuthUiState.Idle
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Idle
                onError(e.localizedMessage ?: "Couldn't reach the server. Check your connection and try again.")
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
            delay(500)

            if (state.typedCode == state.code) {
                _signupVerifyState.value = SignupVerificationState(isOpen = false)
                onSuccess()
            } else {
                val newTriesLeft = state.triesLeft - 1
                if (newTriesLeft <= 0) {
                    _signupVerifyState.value = state.copy(
                        isVerifying = false,
                        triesLeft = 0,
                        isLocked = true,
                        errorMessage = "Too many wrong codes. Try again in 10 minutes."
                    )
                } else {
                    val triesMsg = if (newTriesLeft == 1) "1 try left." else "$newTriesLeft tries left."
                    _signupVerifyState.value = state.copy(
                        isVerifying = false,
                        triesLeft = newTriesLeft,
                        errorMessage = "That code is not right. $triesMsg"
                    )
                }
            }
        }
    }

    fun resendSignupCode(onSent: () -> Unit) {
        val state = _signupVerifyState.value
        val email = state.email
        if (email.isBlank()) return

        val generatedCode = String.format("%06d", (100000..999999).random())

        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("mail").add(mapOf(
                    "to" to email,
                    "message" to mapOf(
                        "subject" to "Your diBadge verification code",
                        "text" to "Your diBadge verification code is $generatedCode. It expires in 10 minutes. If you did not try to create an account, you can ignore this email."
                    )
                ))
            } catch (e: Exception) {}

            _signupVerifyState.value = _signupVerifyState.value.copy(
                code = generatedCode,
                typedCode = "",
                errorMessage = null,
                resendSecondsLeft = 60
            )

            startResendTimer()
            onSent()
        }
    }

    fun dismissSignupVerifySheet(onCancelled: () -> Unit = {}) {
        val state = _signupVerifyState.value
        resendTimerJob?.cancel()

        if (state.isGoogleSignup) {
            val user = auth.currentUser
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    user?.delete()?.await()
                } catch (e: Exception) {}
                auth.signOut()
            }
        }

        _signupVerifyState.value = SignupVerificationState(isOpen = false)
        onCancelled()
    }

    fun signUp(name: String, email: String, pass: String, confirmPass: String) {
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
                        firestore.collection("users").document(user.uid).set(
                            mapOf(
                                "email" to user.email,
                                "emailOtp" to true,
                                "createdAt" to System.currentTimeMillis()
                            )
                        )
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
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser == true
                    if (user != null) {
                        if (isNewUser && !user.email.isNullOrBlank()) {
                            requestSignupCodeInternal(
                                email = user.email!!,
                                isGoogle = true,
                                onSuccess = {},
                                onError = { msg -> _uiState.value = AuthUiState.Error(msg) }
                            )
                        } else {
                            _uiState.value = AuthUiState.Success(user)
                        }
                    } else {
                        _uiState.value = AuthUiState.Error("Google Sign-In failed")
                    }
                } else {
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
