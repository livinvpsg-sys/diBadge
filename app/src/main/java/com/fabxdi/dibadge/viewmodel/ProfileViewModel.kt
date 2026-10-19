package com.fabxdi.dibadge.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.util.UsernameFormatResult
import com.fabxdi.dibadge.util.cleanAndFormatUsernameInput
import com.fabxdi.dibadge.util.fullName
import com.fabxdi.dibadge.util.generateBadgeNumber
import com.fabxdi.dibadge.util.generateUsernameSuggestions
import com.fabxdi.dibadge.util.isValidName
import com.fabxdi.dibadge.util.validateUsernameFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

data class UserProfileData(
    val badgeNumber: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val username: String = "",
    val nickname: String = "",
    val role: String = "",
    val organization: String = "",
    val email: String = "",
    val phone: String = "",
    val phoneCountryCode: String = "+65",
    val country: String = "",
    val birthday: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val signatureUrl: String = ""
)

data class EmailVerificationState(
    val isOpen: Boolean = false,
    val challengeId: String = "",
    val code: String = "",
    val typedCode: String = "",
    val isVerifying: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val triesLeft: Int = 5,
    val isLocked: Boolean = false,
    val isExpired: Boolean = false,
    val resendSecondsLeft: Int = 60
)

sealed class UsernameAvailabilityState {
    object Idle : UsernameAvailabilityState()
    object InvalidFormat : UsernameAvailabilityState()
    object Reserved : UsernameAvailabilityState()
    object Checking : UsernameAvailabilityState()
    data class Available(val name: String) : UsernameAvailabilityState()
    data class Taken(val suggestions: List<String>) : UsernameAvailabilityState()
    object CantCheck : UsernameAvailabilityState()
}

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Saving : ProfileUiState()
    object Success : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _profileData = MutableStateFlow(UserProfileData())
    val profileData: StateFlow<UserProfileData> = _profileData.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _emailVerifyState = MutableStateFlow(EmailVerificationState())
    val emailVerifyState: StateFlow<EmailVerificationState> = _emailVerifyState.asStateFlow()

    private val _usernameAvailability = MutableStateFlow<UsernameAvailabilityState>(UsernameAvailabilityState.Idle)
    val usernameAvailability: StateFlow<UsernameAvailabilityState> = _usernameAvailability.asStateFlow()

    private val _isUploadingPhoto = MutableStateFlow(false)
    val isUploadingPhoto: StateFlow<Boolean> = _isUploadingPhoto.asStateFlow()

    private var initialProfileData = UserProfileData()
    private var resendTimerJob: Job? = null
    private var usernameCheckJob: Job? = null
    private var networkCallbackRegistered = false

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        val user = auth.currentUser ?: run {
            _uiState.value = ProfileUiState.Error("No user signed in")
            return
        }

        val emailVal = user.email ?: ""
        val displayName = user.displayName ?: ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = firestore.collection("users").document(user.uid).get().await()
                if (doc.exists()) {
                    var fn = doc.getString("firstName") ?: ""
                    var mn = doc.getString("middleName") ?: ""
                    var ln = doc.getString("lastName") ?: ""

                    if (fn.isBlank() && ln.isBlank() && displayName.isNotBlank()) {
                        val parts = displayName.trim().split(" ")
                        fn = parts.getOrNull(0) ?: ""
                        if (parts.size > 2) {
                            mn = parts.subList(1, parts.size - 1).joinToString(" ")
                            ln = parts.lastOrNull() ?: ""
                        } else {
                            ln = parts.getOrNull(1) ?: ""
                        }
                    }

                    var badgeNo = doc.getString("badgeNumber") ?: ""
                    if (badgeNo.isBlank()) {
                        badgeNo = generateBadgeNumber()
                        try {
                            firestore.collection("users").document(user.uid)
                                .set(mapOf("badgeNumber" to badgeNo), SetOptions.merge())
                        } catch (e: Exception) {}
                    }

                    val un = doc.getString("username") ?: ""
                    val nn = doc.getString("nickname") ?: ""
                    val roleVal = doc.getString("role") ?: ""
                    val orgVal = doc.getString("organization") ?: doc.getString("company") ?: ""
                    val ph = doc.getString("phone") ?: ""
                    val phCc = doc.getString("phoneCountryCode") ?: "+65"
                    val countryVal = doc.getString("country") ?: ""
                    val bd = doc.getString("birthday") ?: ""
                    val bioText = doc.getString("bio") ?: ""
                    val photo = doc.getString("photoUrl") ?: ""
                    val sig = doc.getString("signatureUrl") ?: ""

                    val loaded = UserProfileData(
                        badgeNumber = badgeNo,
                        firstName = fn,
                        middleName = mn,
                        lastName = ln,
                        username = un,
                        nickname = nn,
                        role = roleVal,
                        organization = orgVal,
                        email = emailVal,
                        phone = ph,
                        phoneCountryCode = phCc,
                        country = countryVal,
                        birthday = bd,
                        bio = bioText,
                        photoUrl = photo,
                        signatureUrl = sig
                    )
                    initialProfileData = loaded
                    _profileData.value = loaded
                } else {
                    val parts = displayName.trim().split(" ")
                    val fn = parts.getOrNull(0) ?: ""
                    val ln = parts.getOrNull(1) ?: ""

                    val badgeNo = generateBadgeNumber()
                    try {
                        firestore.collection("users").document(user.uid)
                            .set(mapOf("badgeNumber" to badgeNo), SetOptions.merge())
                    } catch (e: Exception) {}

                    val loaded = UserProfileData(
                        badgeNumber = badgeNo,
                        firstName = fn,
                        middleName = "",
                        lastName = ln,
                        email = emailVal,
                        photoUrl = ""
                    )
                    initialProfileData = loaded
                    _profileData.value = loaded
                }
                _uiState.value = ProfileUiState.Idle
            } catch (e: Exception) {
                val parts = displayName.trim().split(" ")
                val fn = parts.getOrNull(0) ?: ""
                val ln = parts.getOrNull(1) ?: ""

                val loaded = UserProfileData(
                    badgeNumber = "",
                    firstName = fn,
                    middleName = "",
                    lastName = ln,
                    email = emailVal
                )
                initialProfileData = loaded
                _profileData.value = loaded
                _uiState.value = ProfileUiState.Idle
            }
        }
    }

    fun isUsernameLocked(): Boolean {
        return initialProfileData.username.isNotBlank()
    }

    fun hasChanges(): Boolean {
        val curr = _profileData.value
        val init = initialProfileData
        return curr.firstName.trim() != init.firstName.trim() ||
                curr.middleName.trim() != init.middleName.trim() ||
                curr.lastName.trim() != init.lastName.trim() ||
                curr.username.trim().lowercase() != init.username.trim().lowercase() ||
                curr.nickname.trim() != init.nickname.trim() ||
                curr.role.trim() != init.role.trim() ||
                curr.organization.trim() != init.organization.trim() ||
                curr.email.trim() != init.email.trim() ||
                curr.phone.trim() != init.phone.trim() ||
                curr.phoneCountryCode.trim() != init.phoneCountryCode.trim() ||
                curr.country.trim().uppercase() != init.country.trim().uppercase() ||
                curr.birthday.trim() != init.birthday.trim() ||
                curr.bio.trim() != init.bio.trim() ||
                curr.photoUrl.trim() != init.photoUrl.trim() ||
                curr.signatureUrl.trim() != init.signatureUrl.trim()
    }

    fun isSaveEnabled(): Boolean {
        if (!hasChanges() || _uiState.value is ProfileUiState.Saving) return false

        val currentUsername = _profileData.value.username.trim().lowercase()
        if (!isUsernameLocked() && currentUsername.isNotBlank()) {
            val avail = _usernameAvailability.value
            if (avail !is UsernameAvailabilityState.Available || avail.name != currentUsername) {
                return false
            }
        }
        return true
    }

    fun hasProtectedChanges(): Boolean {
        val curr = _profileData.value
        val init = initialProfileData
        return curr.firstName.trim() != init.firstName.trim() ||
                curr.middleName.trim() != init.middleName.trim() ||
                curr.lastName.trim() != init.lastName.trim() ||
                curr.phone.trim() != init.phone.trim() ||
                curr.phoneCountryCode.trim() != init.phoneCountryCode.trim()
    }

    fun observeNetworkAndAutoRetry(context: Context) {
        if (networkCallbackRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            val builder = NetworkRequest.Builder()
            cm.registerNetworkCallback(
                builder.build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (_usernameAvailability.value is UsernameAvailabilityState.CantCheck) {
                            retryUsernameCheck()
                        }
                    }
                }
            )
            networkCallbackRegistered = true
        } catch (e: Exception) {}
    }

    fun updateFirstName(value: String) {
        _profileData.value = _profileData.value.copy(firstName = value)
    }

    fun updateMiddleName(value: String) {
        _profileData.value = _profileData.value.copy(middleName = value)
    }

    fun updateLastName(value: String) {
        _profileData.value = _profileData.value.copy(lastName = value)
    }

    fun updateUsername(value: String) {
        if (isUsernameLocked()) return

        val clean = cleanAndFormatUsernameInput(value)
        _profileData.value = _profileData.value.copy(username = clean)

        usernameCheckJob?.cancel()

        when (validateUsernameFormat(clean)) {
            UsernameFormatResult.EMPTY -> {
                _usernameAvailability.value = UsernameAvailabilityState.Idle
            }
            UsernameFormatResult.INVALID_FORMAT -> {
                _usernameAvailability.value = UsernameAvailabilityState.InvalidFormat
            }
            UsernameFormatResult.RESERVED -> {
                _usernameAvailability.value = UsernameAvailabilityState.Reserved
            }
            UsernameFormatResult.VALID -> {
                _usernameAvailability.value = UsernameAvailabilityState.Checking
                usernameCheckJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(500)
                    checkUsernameAvailabilityOnServer(clean)
                }
            }
        }
    }

    fun retryUsernameCheck() {
        val currentUsername = _profileData.value.username.trim().lowercase()
        if (isUsernameLocked() || currentUsername.isBlank()) return

        usernameCheckJob?.cancel()
        when (validateUsernameFormat(currentUsername)) {
            UsernameFormatResult.VALID -> {
                _usernameAvailability.value = UsernameAvailabilityState.Checking
                usernameCheckJob = viewModelScope.launch(Dispatchers.IO) {
                    checkUsernameAvailabilityOnServer(currentUsername)
                }
            }
            UsernameFormatResult.INVALID_FORMAT -> _usernameAvailability.value = UsernameAvailabilityState.InvalidFormat
            UsernameFormatResult.RESERVED -> _usernameAvailability.value = UsernameAvailabilityState.Reserved
            UsernameFormatResult.EMPTY -> _usernameAvailability.value = UsernameAvailabilityState.Idle
        }
    }

    private suspend fun checkUsernameAvailabilityOnServer(cleanUsername: String) {
        val user = auth.currentUser ?: return
        try {
            val doc = firestore.collection("usernames").document(cleanUsername).get().await()
            if (doc.exists() && doc.getString("uid") != user.uid) {
                val candidates = generateUsernameSuggestions(
                    baseInput = cleanUsername,
                    firstName = _profileData.value.firstName,
                    lastName = _profileData.value.lastName
                )

                val freeSuggestions = mutableListOf<String>()
                for (cand in candidates) {
                    if (freeSuggestions.size >= 3) break
                    try {
                        val candDoc = firestore.collection("usernames").document(cand).get().await()
                        if (!candDoc.exists()) {
                            freeSuggestions.add(cand)
                        }
                    } catch (e: Exception) {}
                }

                if (_profileData.value.username == cleanUsername) {
                    _usernameAvailability.value = UsernameAvailabilityState.Taken(freeSuggestions)
                }
            } else {
                if (_profileData.value.username == cleanUsername) {
                    _usernameAvailability.value = UsernameAvailabilityState.Available(cleanUsername)
                }
            }
        } catch (e: Exception) {
            if (_profileData.value.username == cleanUsername) {
                _usernameAvailability.value = UsernameAvailabilityState.CantCheck
            }
        }
    }

    fun updateNickname(value: String) {
        _profileData.value = _profileData.value.copy(nickname = value.take(20))
    }

    fun updateRole(value: String) {
        _profileData.value = _profileData.value.copy(role = value.take(60))
    }

    fun updateOrganization(value: String) {
        _profileData.value = _profileData.value.copy(organization = value.take(60))
    }

    fun updatePhone(value: String) {
        _profileData.value = _profileData.value.copy(phone = value)
    }

    fun updatePhoneCountryCode(code: String) {
        _profileData.value = _profileData.value.copy(phoneCountryCode = code)
    }

    fun updateCountry(isoCode: String) {
        _profileData.value = _profileData.value.copy(country = isoCode.trim().uppercase())
    }

    fun updateBirthday(value: String) {
        _profileData.value = _profileData.value.copy(birthday = value)
    }

    fun updateBio(value: String) {
        _profileData.value = _profileData.value.copy(bio = value.take(150))
    }

    fun updateSignatureUrl(url: String) {
        _profileData.value = _profileData.value.copy(signatureUrl = url)
    }

    fun updateTypedCode(typed: String) {
        val current = _emailVerifyState.value
        _emailVerifyState.value = current.copy(
            typedCode = typed,
            errorMessage = if (current.errorMessage?.contains("not right") == true) null else current.errorMessage
        )
    }

    fun dismissEmailVerifySheet() {
        resendTimerJob?.cancel()
        _emailVerifyState.value = EmailVerificationState(isOpen = false)
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            for (sec in 60 downTo 0) {
                _emailVerifyState.value = _emailVerifyState.value.copy(resendSecondsLeft = sec)
                delay(1000)
            }
        }
    }

    fun requestEmailVerificationCode(onVerifyRequired: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: run {
            onError("No user signed in")
            return
        }

        if (user.email.isNullOrBlank()) {
            onError("VERIFY_EMAIL_FIRST")
            return
        }

        val generatedCode = String.format("%06d", (100000..999999).random())
        val challengeId = UUID.randomUUID().toString()

        _uiState.value = ProfileUiState.Saving

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val challengeData = mapOf(
                    "challengeId" to challengeId,
                    "code" to generatedCode,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to System.currentTimeMillis() + 10 * 60 * 1000,
                    "attempts" to 0
                )

                firestore.collection("users")
                    .document(user.uid)
                    .collection("profileChallenges")
                    .document(challengeId)
                    .set(challengeData)
                    .await()

                try {
                    firestore.collection("mail").add(mapOf(
                        "to" to user.email,
                        "message" to mapOf(
                            "subject" to "Your diBadge verification code",
                            "text" to "Your diBadge verification code is $generatedCode. It expires in 10 minutes."
                        )
                    ))
                } catch (e: Exception) {}

                _emailVerifyState.value = EmailVerificationState(
                    isOpen = true,
                    challengeId = challengeId,
                    code = generatedCode,
                    typedCode = "",
                    resendSecondsLeft = 60
                )

                startResendTimer()
                _uiState.value = ProfileUiState.Idle
                onVerifyRequired()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Idle
                onError(e.localizedMessage ?: "Failed to send verification code")
            }
        }
    }

    fun verifyCode(onSuccess: () -> Unit) {
        val state = _emailVerifyState.value
        if (state.typedCode.length != 6 || state.isVerifying || state.isLocked) return

        _emailVerifyState.value = state.copy(isVerifying = true)

        viewModelScope.launch(Dispatchers.IO) {
            delay(500)

            if (state.typedCode == state.code) {
                saveProfileInternal(
                    onSuccess = {
                        _emailVerifyState.value = EmailVerificationState(isOpen = false)
                        onSuccess()
                    },
                    onError = { msg ->
                        _emailVerifyState.value = _emailVerifyState.value.copy(
                            isVerifying = false,
                            errorMessage = msg
                        )
                    }
                )
            } else {
                val newTriesLeft = state.triesLeft - 1
                if (newTriesLeft <= 0) {
                    _emailVerifyState.value = state.copy(
                        isVerifying = false,
                        triesLeft = 0,
                        isLocked = true,
                        errorMessage = "Too many wrong codes. Try again in 10 minutes."
                    )
                } else {
                    val triesMsg = if (newTriesLeft == 1) "1 try left." else "$newTriesLeft tries left."
                    _emailVerifyState.value = state.copy(
                        isVerifying = false,
                        triesLeft = newTriesLeft,
                        errorMessage = "That code is not right. $triesMsg"
                    )
                }
            }
        }
    }

    fun resendCode(onSent: () -> Unit) {
        val user = auth.currentUser ?: return
        val generatedCode = String.format("%06d", (100000..999999).random())

        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("mail").add(mapOf(
                    "to" to user.email,
                    "message" to mapOf(
                        "subject" to "Your diBadge verification code",
                        "text" to "Your diBadge verification code is $generatedCode. It expires in 10 minutes."
                    )
                ))
            } catch (e: Exception) {}

            _emailVerifyState.value = _emailVerifyState.value.copy(
                code = generatedCode,
                typedCode = "",
                errorMessage = null,
                resendSecondsLeft = 60
            )

            startResendTimer()
            onSent()
        }
    }

    fun saveCroppedPhoto(context: Context, bitmap: Bitmap, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: run {
            onComplete(false, "No user signed in")
            return
        }

        _isUploadingPhoto.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scaledBitmap = if (bitmap.width > 512 || bitmap.height > 512) {
                    Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                } else {
                    bitmap
                }

                val baos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val data = baos.toByteArray()

                val localFile = File(context.filesDir, "profile_photo.jpg")
                localFile.writeBytes(data)
                val localUriStr = "file://${localFile.absolutePath}?v=${System.currentTimeMillis()}"

                _profileData.value = _profileData.value.copy(photoUrl = localUriStr)
                initialProfileData = initialProfileData.copy(photoUrl = localUriStr)

                firestore.collection("users")
                    .document(user.uid)
                    .set(mapOf("photoUrl" to localUriStr, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                    .await()

                _isUploadingPhoto.value = false
                onComplete(true, localUriStr)

                try {
                    val storageRef = storage.reference.child("users/${user.uid}/profile_photo.jpg")
                    storageRef.putBytes(data).await()
                    val rawUrl = storageRef.downloadUrl.await().toString()
                    val cloudUrl = "$rawUrl?v=${System.currentTimeMillis()}"

                    firestore.collection("users")
                        .document(user.uid)
                        .set(mapOf("photoUrl" to cloudUrl, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                        .await()

                    _profileData.value = _profileData.value.copy(photoUrl = cloudUrl)
                    initialProfileData = initialProfileData.copy(photoUrl = cloudUrl)
                } catch (e: Exception) {}
            } catch (e: Exception) {
                _isUploadingPhoto.value = false
                onComplete(false, e.localizedMessage ?: "Couldn't update the photo. Try again.")
            }
        }
    }

    fun deletePhoto(context: Context, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: run {
            onComplete(false)
            return
        }

        _isUploadingPhoto.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localFile = File(context.filesDir, "profile_photo.jpg")
                if (localFile.exists()) localFile.delete()

                try {
                    storage.reference.child("users/${user.uid}/profile_photo.jpg").delete().await()
                } catch (e: Exception) {}

                firestore.collection("users")
                    .document(user.uid)
                    .set(mapOf("photoUrl" to "", "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                    .await()

                _profileData.value = _profileData.value.copy(photoUrl = "")
                initialProfileData = initialProfileData.copy(photoUrl = "")
                _isUploadingPhoto.value = false
                onComplete(true)
            } catch (e: Exception) {
                _isUploadingPhoto.value = false
                onComplete(false)
            }
        }
    }

    private suspend fun uploadSignatureIfNeeded(userId: String, sigUrl: String): String {
        if (sigUrl.isBlank() || !sigUrl.startsWith("data:image")) return sigUrl
        return try {
            val base64Str = sigUrl.substringAfter("base64,")
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val storageRef = storage.reference.child("users/$userId/signature.png")
            storageRef.putBytes(decodedBytes).await()
            val rawUrl = storageRef.downloadUrl.await().toString()
            "$rawUrl?v=${System.currentTimeMillis()}"
        } catch (e: Exception) {
            sigUrl
        }
    }

    private suspend fun uploadPhotoIfNeeded(userId: String, photoUrl: String): String {
        if (photoUrl.isBlank() || !photoUrl.startsWith("file://")) return photoUrl
        return try {
            val filePath = photoUrl.substringAfter("file://").substringBefore("?")
            val file = File(filePath)
            if (!file.exists()) return photoUrl

            val bytes = file.readBytes()
            val storageRef = storage.reference.child("users/$userId/profile_photo.jpg")
            storageRef.putBytes(bytes).await()
            val rawUrl = storageRef.downloadUrl.await().toString()
            "$rawUrl?v=${System.currentTimeMillis()}"
        } catch (e: Exception) {
            photoUrl
        }
    }

    fun saveProfile(onVerifyRequired: () -> Unit, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val current = _profileData.value

        if (current.firstName.trim().isBlank()) {
            onError("First name is required")
            return
        }

        if (!isValidName(current.firstName)) {
            onError("FIRST_NAME_INVALID")
            return
        }

        if (current.middleName.isNotBlank() && !isValidName(current.middleName)) {
            onError("MIDDLE_NAME_INVALID")
            return
        }

        if (current.lastName.isNotBlank() && !isValidName(current.lastName)) {
            onError("LAST_NAME_INVALID")
            return
        }

        if (!isUsernameLocked() && current.username.isNotBlank()) {
            val avail = _usernameAvailability.value
            if (avail !is UsernameAvailabilityState.Available || avail.name != current.username.trim().lowercase()) {
                onError("USERNAME_NOT_AVAILABLE")
                return
            }
        }

        if (hasProtectedChanges()) {
            requestEmailVerificationCode(
                onVerifyRequired = onVerifyRequired,
                onError = { msg ->
                    if (msg == "VERIFY_EMAIL_FIRST") {
                        onError("VERIFY_EMAIL_FIRST")
                    } else {
                        onError(msg)
                    }
                }
            )
        } else {
            saveProfileInternal(onSuccess, onError)
        }
    }

    private fun saveProfileInternal(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return
        val current = _profileData.value
        val newUsernameLower = current.username.lowercase().trim()

        _uiState.value = ProfileUiState.Saving

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalSignatureUrl = uploadSignatureIfNeeded(user.uid, current.signatureUrl)
                val finalPhotoUrl = uploadPhotoIfNeeded(user.uid, current.photoUrl)

                firestore.runTransaction { transaction ->
                    val userDocRef = firestore.collection("users").document(user.uid)
                    val userDoc = transaction.get(userDocRef)

                    val existingUsername = userDoc.getString("username")
                    if (!existingUsername.isNullOrBlank() && existingUsername != newUsernameLower) {
                        throw IllegalStateException("USERNAME_LOCKED")
                    }

                    if (newUsernameLower.isNotBlank() && existingUsername.isNullOrBlank()) {
                        val usernameDocRef = firestore.collection("usernames").document(newUsernameLower)
                        val usernameDoc = transaction.get(usernameDocRef)

                        if (usernameDoc.exists() && usernameDoc.getString("uid") != user.uid) {
                            throw IllegalStateException("USERNAME_TAKEN")
                        }

                        transaction.set(
                            usernameDocRef,
                            mapOf(
                                "uid" to user.uid,
                                "createdAt" to System.currentTimeMillis()
                            )
                        )

                        transaction.update(
                            userDocRef,
                            mapOf(
                                "username" to newUsernameLower,
                                "usernameLower" to newUsernameLower
                            )
                        )
                    }

                    val combinedDisplayName = fullName(current.firstName, current.middleName, current.lastName)
                    val updates = mapOf(
                        "firstName" to current.firstName.trim(),
                        "middleName" to current.middleName.trim(),
                        "lastName" to current.lastName.trim(),
                        "displayName" to combinedDisplayName,
                        "nickname" to current.nickname.trim(),
                        "role" to current.role.trim(),
                        "organization" to current.organization.trim(),
                        "phone" to current.phone.trim(),
                        "phoneCountryCode" to current.phoneCountryCode.trim(),
                        "country" to current.country.trim().uppercase(),
                        "birthday" to current.birthday.trim(),
                        "bio" to current.bio.trim(),
                        "photoUrl" to finalPhotoUrl,
                        "signatureUrl" to finalSignatureUrl,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    transaction.set(userDocRef, updates, SetOptions.merge())
                }.await()

                val combinedDisplayName = fullName(current.firstName, current.middleName, current.lastName)
                if (combinedDisplayName.isNotBlank()) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(combinedDisplayName)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }

                initialProfileData = current.copy(
                    firstName = current.firstName.trim(),
                    middleName = current.middleName.trim(),
                    lastName = current.lastName.trim(),
                    username = newUsernameLower,
                    nickname = current.nickname.trim(),
                    role = current.role.trim(),
                    organization = current.organization.trim(),
                    phone = current.phone.trim(),
                    phoneCountryCode = current.phoneCountryCode.trim(),
                    country = current.country.trim().uppercase(),
                    photoUrl = finalPhotoUrl,
                    signatureUrl = finalSignatureUrl
                )
                _profileData.value = initialProfileData
                _usernameAvailability.value = UsernameAvailabilityState.Idle
                _uiState.value = ProfileUiState.Success
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Idle
                val msg = e.message ?: e.localizedMessage ?: "Failed to save profile"
                if (msg.contains("USERNAME_TAKEN")) {
                    onError("USERNAME_TAKEN")
                } else if (msg.contains("USERNAME_LOCKED")) {
                    onError("USERNAME_LOCKED")
                } else if (e is IOException || msg.contains("unavailable") || msg.contains("network")) {
                    _usernameAvailability.value = UsernameAvailabilityState.CantCheck
                    onError("NETWORK_ERROR")
                } else {
                    onError(msg)
                }
            }
        }
    }
}
