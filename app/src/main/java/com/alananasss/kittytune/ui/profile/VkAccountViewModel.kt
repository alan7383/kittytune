package com.alananasss.kittytune.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.vk.VkRepository
import com.alananasss.kittytune.data.vk.VkTokenManager
import com.alananasss.kittytune.data.vk.VkUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VkAccountViewModel(application: Application) : AndroidViewModel(application) {

    val tokenManager = VkTokenManager(application)
    private val repository = VkRepository.getInstance(application)

    var user by mutableStateOf<VkUser?>(tokenManager.getUser())
        private set

    var isLoggedIn by mutableStateOf(tokenManager.isLoggedIn())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var tracksCount by mutableIntStateOf(0)
        private set

    var playlistsCount by mutableIntStateOf(0)
        private set

    var includeInSearch by mutableStateOf(tokenManager.includeInSearch)
        private set

    var includeInRecommendations by mutableStateOf(tokenManager.includeInRecommendations)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Result of the last connection diagnostic, shown verbatim so testers can copy it. */
    var diagnosticsReport by mutableStateOf<String?>(null)
        private set

    var isDiagnosing by mutableStateOf(false)
        private set

    fun runDiagnostics() {
        if (isDiagnosing) return
        viewModelScope.launch {
            isDiagnosing = true
            diagnosticsReport = try {
                withContext(Dispatchers.IO) {
                    com.alananasss.kittytune.data.vk.VkApi(getApplication()).selfTest()
                }
            } catch (e: Exception) {
                "Diagnostics failed: ${e.javaClass.simpleName}: ${e.message}"
            }
            isDiagnosing = false
        }
    }

    fun dismissDiagnostics() {
        diagnosticsReport = null
    }

    init {
        loadAccount()
    }

    fun loadAccount(forceRefresh: Boolean = false) {
        isLoggedIn = tokenManager.isLoggedIn()
        if (!isLoggedIn) {
            user = null
            tracksCount = 0
            playlistsCount = 0
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
                withContext(Dispatchers.IO) {
                    val profile = repository.refreshUserProfile()
                    user = profile ?: tokenManager.getUser()

                    try {
                        val audios = repository.getUserAudios(offset = 0, count = 1)
                        tracksCount = audios.totalCount.takeIf { it > 0 } ?: audios.tracks.size
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        val playlists = repository.getPlaylists()
                        playlistsCount = playlists.size
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.localizedMessage ?: "Failed to load VK account"
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun loginWithCookies(
        remixsid: String,
        remixnsid: String? = null,
        remixdsid: String? = null,
        userId: Long
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                tokenManager.saveSession(
                    remixsid = remixsid,
                    remixnsid = remixnsid,
                    remixdsid = remixdsid,
                    userId = userId
                )
                isLoggedIn = true
                loadAccount(forceRefresh = true)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Login failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun loginWithToken(
        token: String,
        userId: Long,
        remixsid: String? = null,
        remixnsid: String? = null,
        remixdsid: String? = null,
        firstName: String = "",
        lastName: String = "",
        photoUrl: String? = null,
        screenName: String? = null
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                tokenManager.saveSession(
                    remixsid = remixsid,
                    remixnsid = remixnsid,
                    remixdsid = remixdsid,
                    userId = userId,
                    accessToken = token,
                    firstName = firstName,
                    lastName = lastName,
                    photoUrl = photoUrl,
                    screenName = screenName
                )
                isLoggedIn = true
                user = tokenManager.getUser()
                loadAccount(forceRefresh = true)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Login failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        tokenManager.clearSession()
        isLoggedIn = false
        user = null
        tracksCount = 0
        playlistsCount = 0
    }

    fun setIncludeInSearchSetting(enabled: Boolean) {
        tokenManager.includeInSearch = enabled
        includeInSearch = enabled
    }

    fun setIncludeInRecommendationsSetting(enabled: Boolean) {
        tokenManager.includeInRecommendations = enabled
        includeInRecommendations = enabled
    }
}
