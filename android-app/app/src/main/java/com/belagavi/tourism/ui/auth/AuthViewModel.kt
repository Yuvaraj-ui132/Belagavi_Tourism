package com.belagavi.tourism.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.android.gms.auth.api.signin.GoogleSignInClient

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPasswordResetSent = MutableStateFlow(false)
    val isPasswordResetSent: StateFlow<Boolean> = _isPasswordResetSent.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        // Observe auth state changes
        _currentUser.value = firebaseAuth.currentUser
        _isLoggedIn.value = firebaseAuth.currentUser != null
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
            _isLoggedIn.value = auth.currentUser != null
        }
    }

    fun loginUser(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email and Password cannot be empty."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                _isLoggedIn.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Authentication failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerUser(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _errorMessage.value = "All fields are required."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isRegistered.value = false
            try {
                val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    // Update user profile display name if needed, but Firebase Auth display name is enough
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }
                _isRegistered.value = true
                _isLoggedIn.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Registration failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isPasswordResetSent.value = false
            try {
                firebaseAuth.sendPasswordResetEmail(email).await()
                _isPasswordResetSent.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to send reset email."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithGoogle(credential: com.google.firebase.auth.AuthCredential) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                firebaseAuth.signInWithCredential(credential).await()
                _isLoggedIn.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Google Sign-In failed. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
        _isLoggedIn.value = false
        _currentUser.value = null
    }

    fun reloadCurrentUser() {
        viewModelScope.launch {
            try {
                firebaseAuth.currentUser?.reload()?.await()
                _currentUser.value = firebaseAuth.currentUser
            } catch (e: Exception) {
                // Fail silently or fallback
                _currentUser.value = firebaseAuth.currentUser
            }
        }
    }

    fun updateDisplayName(newName: String) {
        val user = firebaseAuth.currentUser ?: return
        viewModelScope.launch {
            try {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                user.updateProfile(profileUpdates).await()
                reloadCurrentUser()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update profile name."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun resetPasswordSentState() {
        _isPasswordResetSent.value = false
    }

    fun resetRegisteredState() {
        _isRegistered.value = false
    }
}

