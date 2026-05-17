    package com.alananasss.kittytune.data
    
    import android.content.Context
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.data.network.SoundCloudApi
    import com.alananasss.kittytune.domain.User
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.flow.update
    import kotlinx.coroutines.launch
    
object RepostRepository {
        private lateinit var api: SoundCloudApi
        private lateinit var appContext: Context
        private var currentUserId: Long = 0L // cache the user id
    
        private val scope = CoroutineScope(Dispatchers.IO)
    
        private val _repostedTrackIds = MutableStateFlow<Set<Long>>(emptySet())
        val repostedTrackIds = _repostedTrackIds.asStateFlow()
    
        fun init(context: Context) {
            appContext = context.applicationContext
            api = RetrofitClient.create(context)
        }
    
        fun clearUser() {
            currentUserId = 0L
        }
    
        // fetches the complete list of reposts from the server to sync local state
        fun refreshReposts() {
            scope.launch {
                try {
                    val tokenManager = TokenManager(appContext)
                    if (tokenManager.isGuestMode() || tokenManager.getAccessToken().isNullOrEmpty()) {
                        return@launch
                    }

                    // fetch user id if missing
                    if (currentUserId == 0L) {
                        val me: User = api.getMe()
                        currentUserId = me.id
                    }
                    if (currentUserId == 0L) return@launch
    
                    val allRepostTrackIds = mutableSetOf<Long>()
                    var nextUrl: String? = null
                    var initialCall = true
    
                    // handle pagination
                    while (initialCall || nextUrl != null) {
                        val response = if (initialCall) {
                            initialCall = false
                            api.getUserReposts(userId = currentUserId, limit = 200)
                        } else {
                            api.getRepostsNextPage(nextUrl!!)
                        }
    
                        val idsFromPage = response.collection.mapNotNull { it.track?.id }
                        allRepostTrackIds.addAll(idsFromPage)
    
                        nextUrl = response.next_href
                    }
    
                    _repostedTrackIds.value = allRepostTrackIds
    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    
        // optimistic add (trigger network automatically)
        fun addRepost(trackId: Long) {
            _repostedTrackIds.update { it + trackId }
            scope.launch {
                try {
                    api.repostTrack(trackId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    _repostedTrackIds.update { it - trackId } // rollback on failure
                }
            }
        }
    
        // optimistic remove (trigger network automatically)
        fun removeRepost(trackId: Long) {
            _repostedTrackIds.update { it - trackId }
            scope.launch {
                try {
                    api.deleteRepost(trackId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    _repostedTrackIds.update { it + trackId } // rollback on failure
                }
            }
        }
    
        // new: update local state only (used when viewmodel handles network)
        fun syncLocalState(trackId: Long, isReposted: Boolean) {
            if (isReposted) {
                _repostedTrackIds.update { it + trackId }
            } else {
                _repostedTrackIds.update { it - trackId }
            }
        }
    }


