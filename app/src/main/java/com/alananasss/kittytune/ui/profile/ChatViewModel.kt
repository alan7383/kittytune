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
    import com.alananasss.kittytune.domain.InboxMessage
    import com.alananasss.kittytune.domain.InboxSender
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
    
        val messages = mutableStateListOf<InboxMessage>()
        var isLoading by mutableStateOf(true)
        var isSending by mutableStateOf(false)
    
        var myUserUrn by mutableStateOf("")
        var currentConversationId: String? = null
    
        val linkMetadataCache = mutableStateMapOf<String, Any?>()
        private val processedLinks = mutableSetOf<String>()
    
        private var pollingJob: Job? = null
    
        private val pendingSentContents = mutableListOf<String>()
    
        fun loadMessages(conversationId: String) {
            currentConversationId = conversationId
            stopPolling()
    
            viewModelScope.launch {
                isLoading = true
                try {
                    val meResponse = api.getMeMobile()
                    val me = meResponse.user
                    myUserUrn = me.urn ?: "soundcloud:users:${me.id}"
    
                    val response = api.getConversationMessages(conversationId)
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
                val convId = currentConversationId ?: return@launch
    
                while (isActive) {
                    delay(5000)
                    try {
                        val response = api.getConversationMessages(convId, limit = 10)
    
                        val newMessages = response.collection.filter { serverMsg ->
                            val isAlreadyDisplayed = messages.any { local ->
                                local.urn == serverMsg.urn ||
                                    (local.content == serverMsg.content &&
                                        local.sender?.urn == serverMsg.sender?.urn &&
                                        (local.sentAt == serverMsg.sentAt ||
                                            (serverMsg.sender?.urn == myUserUrn && serverMsg.content in pendingSentContents)))
                            }
                            !isAlreadyDisplayed
                        }
    
                        if (newMessages.isNotEmpty()) {
                            newMessages.forEach { serverMsg ->
                                if (serverMsg.sender?.urn == myUserUrn && serverMsg.content in pendingSentContents) {
                                    val localIndex = messages.indexOfFirst { local ->
                                        local.sender?.urn == myUserUrn && local.content == serverMsg.content
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
            val convId = currentConversationId ?: return
            if (text.isBlank()) return
    
            viewModelScope.launch {
                isSending = true
                stopPolling()
    
                try {
                    val sentResponse = api.sendMessage(convId, SendMessageRequest(contents = text))
    
                    val currentTimestamp = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
                    ).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
    
                    val displayMessage = InboxMessage(
                        urn = sentResponse.urn,
                        content = text,
                        conversationId = convId,
                        sender = null,
                        sentAt = currentTimestamp
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


