package com.alananasss.kittytune.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundCloudAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    val tokenManager = TokenManager(application)

    var user by mutableStateOf<User?>(null)
        private set

    var configuration by mutableStateOf<com.alananasss.kittytune.domain.SoundCloudConfigurationResponse?>(null)
        private set

    var emails by mutableStateOf<List<com.alananasss.kittytune.domain.MeEmail>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isGuest by mutableStateOf(tokenManager.isGuestMode() || !tokenManager.hasAccessToken())
        private set

    init {
        loadAccount()
    }

    fun loadAccount(forceRefresh: Boolean = false) {
        val app = getApplication<Application>()
        isGuest = tokenManager.isGuestMode() || !tokenManager.hasAccessToken()
        if (isGuest) {
            user = null
            configuration = null
            emails = emptyList()
            isLoading = false
            return
        }

        viewModelScope.launch {
            if (forceRefresh) {
                isRefreshing = true
            } else {
                isLoading = true
            }
            errorMessage = null

            try {
                if (forceRefresh) {
                    SessionManager.harvestStoredSession(app)
                }
                withContext(Dispatchers.IO) {
                    val me = api.getMe()
                    user = me
                    try {
                        val config = api.getAndroidConfiguration()
                        configuration = config
                    } catch (configEx: Exception) {
                        configEx.printStackTrace()
                    }
                    try {
                        val emailList = api.getMeEmails()
                        emails = emailList
                    } catch (emailEx: Exception) {
                        emailEx.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.localizedMessage ?: "Unknown error"
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun resendConfirmationEmail(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.resendEmailConfirmation()
                }
                if (response.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, response.errorBody()?.string() ?: "Failed to resend confirmation")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        tokenManager.logout()
        isGuest = true
        user = null
        onLoggedOut()
    }
}
