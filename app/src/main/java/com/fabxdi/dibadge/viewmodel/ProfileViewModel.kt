package com.fabxdi.dibadge.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class UserProfileData(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val nickname: String = "",
    val email: String = "",
    val phone: String = "",
    val birthday: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val signatureUrl: String = ""
)

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

    private var initialProfileData = UserProfileData()

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
                    var ln = doc.getString("lastName") ?: ""

                    if (fn.isBlank() && ln.isBlank() && displayName.isNotBlank()) {
                        val parts = displayName.trim().split(" ", limit = 2)
                        fn = parts.getOrNull(0) ?: ""
                        ln = parts.getOrNull(1) ?: ""
                    }

                    val un = doc.getString("username") ?: ""
                    val nn = doc.getString("nickname") ?: ""
                    val ph = doc.getString("phone") ?: ""
                    val bd = doc.getString("birthday") ?: ""
                    val bioText = doc.getString("bio") ?: ""
                    val photo = doc.getString("photoUrl") ?: ""
                    val sig = doc.getString("signatureUrl") ?: ""

                    val loaded = UserProfileData(
                        firstName = fn,
                        lastName = ln,
                        username = un,
                        nickname = nn,
                        email = emailVal,
                        phone = ph,
                        birthday = bd,
                        bio = bioText,
                        photoUrl = photo,
                        signatureUrl = sig
                    )
                    initialProfileData = loaded
                    _profileData.value = loaded
                } else {
                    val parts = displayName.trim().split(" ", limit = 2)
                    val fn = parts.getOrNull(0) ?: ""
                    val ln = parts.getOrNull(1) ?: ""

                    val loaded = UserProfileData(
                        firstName = fn,
                        lastName = ln,
                        email = emailVal,
                        photoUrl = ""
                    )
                    initialProfileData = loaded
                    _profileData.value = loaded
                }
                _uiState.value = ProfileUiState.Idle
            } catch (e: Exception) {
                val parts = displayName.trim().split(" ", limit = 2)
                val fn = parts.getOrNull(0) ?: ""
                val ln = parts.getOrNull(1) ?: ""

                val loaded = UserProfileData(
                    firstName = fn,
                    lastName = ln,
                    email = emailVal
                )
                initialProfileData = loaded
                _profileData.value = loaded
                _uiState.value = ProfileUiState.Idle
            }
        }
    }

    fun hasChanges(): Boolean {
        return _profileData.value != initialProfileData
    }

    fun updateFirstName(value: String) {
        _profileData.value = _profileData.value.copy(firstName = value)
    }

    fun updateLastName(value: String) {
        _profileData.value = _profileData.value.copy(lastName = value)
    }

    fun updateUsername(value: String) {
        val clean = value.lowercase().filter { it.isLowerCase() || it.isDigit() || it == '.' || it == '_' }
        _profileData.value = _profileData.value.copy(username = clean.take(20))
    }

    fun updateNickname(value: String) {
        _profileData.value = _profileData.value.copy(nickname = value.take(20))
    }

    fun updatePhone(value: String) {
        _profileData.value = _profileData.value.copy(phone = value)
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

    fun removePhoto() {
        _profileData.value = _profileData.value.copy(photoUrl = "")
    }

    fun uploadPhoto(context: Context, imageUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    onComplete(false, "Failed to decode image")
                    return@launch
                }

                val size = Math.min(originalBitmap.width, originalBitmap.height)
                val x = (originalBitmap.width - size) / 2
                val y = (originalBitmap.height - size) / 2
                val croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, size, size)

                val scaledBitmap = if (size > 512) {
                    Bitmap.createScaledBitmap(croppedBitmap, 512, 512, true)
                } else {
                    croppedBitmap
                }

                val baos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val data = baos.toByteArray()

                val storageRef = storage.reference.child("users/${user.uid}/profile_photo.jpg")
                val uploadTask = storageRef.putBytes(data).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                _profileData.value = _profileData.value.copy(photoUrl = downloadUrl)
                onComplete(true, downloadUrl)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Failed to upload photo")
            }
        }
    }

    fun saveProfile(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return
        val current = _profileData.value

        if (current.firstName.trim().isBlank()) {
            onError("First name is required")
            return
        }

        if (current.username.isNotBlank()) {
            if (current.username.length < 3) {
                onError("Username must be 3-20 characters")
                return
            }
        }

        _uiState.value = ProfileUiState.Saving

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check Username uniqueness if changed
                if (current.username.isNotBlank() && current.username != initialProfileData.username) {
                    val unDoc = firestore.collection("usernames").document(current.username).get().await()
                    if (unDoc.exists() && unDoc.getString("uid") != user.uid) {
                        _uiState.value = ProfileUiState.Idle
                        onError("Username already taken")
                        return@launch
                    }

                    // Claim username in transaction
                    firestore.runTransaction { transaction ->
                        if (initialProfileData.username.isNotBlank()) {
                            transaction.delete(firestore.collection("usernames").document(initialProfileData.username))
                        }
                        transaction.set(
                            firestore.collection("usernames").document(current.username),
                            mapOf("uid" to user.uid, "updatedAt" to System.currentTimeMillis())
                        )
                    }.await()
                }

                val combinedDisplayName = "${current.firstName.trim()} ${current.lastName.trim()}".trim()

                val updates = mapOf(
                    "firstName" to current.firstName.trim(),
                    "lastName" to current.lastName.trim(),
                    "displayName" to combinedDisplayName,
                    "username" to current.username.trim(),
                    "nickname" to current.nickname.trim(),
                    "phone" to current.phone.trim(),
                    "birthday" to current.birthday.trim(),
                    "bio" to current.bio.trim(),
                    "photoUrl" to current.photoUrl,
                    "signatureUrl" to current.signatureUrl,
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(user.uid).set(updates, SetOptions.merge()).await()

                if (combinedDisplayName.isNotBlank()) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(combinedDisplayName)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }

                initialProfileData = current.copy(
                    firstName = current.firstName.trim(),
                    lastName = current.lastName.trim(),
                    username = current.username.trim(),
                    nickname = current.nickname.trim()
                )
                _profileData.value = initialProfileData
                _uiState.value = ProfileUiState.Success
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Idle
                onError(e.localizedMessage ?: "Failed to save profile")
            }
        }
    }
}
