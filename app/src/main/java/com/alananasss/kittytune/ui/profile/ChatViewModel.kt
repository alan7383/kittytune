    package com.alananasss.kittytune.ui.profile
    
    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateMapOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Message
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.SendMessageRequest
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.google.gson.Gson
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.isActive
    import kotlinx.coroutines.launch
    
    class ChatViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
        private val gson = Gson()
    
        val messages = mutableStateListOf<Message>()
        var isLoading by mutableStateOf(true)
        var isSending by mutableStateOf(false)
    
        var myUser by mutableStateOf<User?>(null)
        var currentOtherUserId: String? = null
    
        val linkMetadataCache = mutableStateMapOf<String, Any?>()
        private val processedLinks = mutableSetOf<String>()
    
        private var pollingJob: Job? = null
    
        private val pendingSentContents = mutableListOf<String>()
    
        fun loadMessages(otherUserId: String) {
            currentOtherUserId = otherUserId
            stopPolling()
    
            viewModelScope.launch {
                isLoading = true
                try {
                    val me = api.getMe()
                    myUser = me
    
                    val response = api.getConversationMessages(me.id, otherUserId)
                    messages.clear()
                    pendingSentContents.clear()
                    messages.addAll(response.collection)
    
                    startPolling()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        private fun startPolling() {
            if (pollingJob?.isActive == true) return
    
            pollingJob = viewModelScope.launch {
                val meId = myUser?.id ?: return@launch
                val otherId = currentOtherUserId ?: return@launch
    
                while (isActive) {
                    delay(5000)
                    try {
                        val response = api.getConversationMessages(meId, otherId, limit = 10)
    
                        val newMessages = response.collection.filter { serverMsg ->
                            val isAlreadyDisplayed = messages.any { local ->
                                local.content == serverMsg.content &&
                                        local.sender?.id == serverMsg.sender?.id &&
                                        (local.sentAt == serverMsg.sentAt ||
                                                (serverMsg.sender?.id == myUser?.id && serverMsg.content in pendingSentContents))
                            }
                            !isAlreadyDisplayed
                        }
    
                        if (newMessages.isNotEmpty()) {
                            newMessages.forEach { serverMsg ->
                                if (serverMsg.sender?.id == myUser?.id && serverMsg.content in pendingSentContents) {
                                    val localIndex = messages.indexOfFirst { local ->
                                        local.sender?.id == myUser?.id && local.content == serverMsg.content
                                    }
                                    if (localIndex != -1) {
                                        messages[localIndex] = serverMsg
                                        pendingSentContents.remove(serverMsg.content)
                                    }
                                } else {
                                    messages.add(0, serverMsg)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    
        fun stopPolling() {
            pollingJob?.cancel()
            pollingJob = null
        }
    
        override fun onCleared() {
            super.onCleared()
            stopPolling()
        }
    
        fun sendMessage(text: String) {
            val targetId = currentOtherUserId ?: return
            val me = myUser ?: return
            if (text.isBlank()) return
    
            viewModelScope.launch {
                isSending = true
                stopPolling()
    
                try {
                    val request = SendMessageRequest(contents = text)
                    val newMessage = api.sendMessage(me.id, targetId, request)
    
                    val currentTimestamp = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
                    ).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
    
                    val displayMessage = newMessage.copy(
                        sender = me,
                        sentAt = newMessage.sentAt ?: currentTimestamp,
                        content = text
                    )
    
                    messages.add(0, displayMessage)
                    pendingSentContents.add(text)
    
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isSending = false
                    delay(5500)
                    startPolling()
                }
            }
        }
    
        fun fetchLinkMetadata(url: String) {
            if (linkMetadataCache.containsKey(url) || processedLinks.contains(url)) return
    
            processedLinks.add(url)
            viewModelScope.launch {
                try {
                    val jsonObject = api.resolveUrl(url)
                    val kind = jsonObject.get("kind")?.asString
    
                    val result: Any? = when (kind) {
                        "track" -> gson.fromJson(jsonObject, Track::class.java)
                        "playlist" -> gson.fromJson(jsonObject, Playlist::class.java)
                        "user" -> gson.fromJson(jsonObject, User::class.java)
                        else -> null
                    }
    
                    if (result != null) {
                        linkMetadataCache[url] = result
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    linkMetadataCache[url] = null
                }
            }
        }
    }


