package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object SocialProofRepository {
    private const val TAG = "SocialProofRepo"
    private var api: SoundCloudApi? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Cache of trackId -> List<User>
    private val _socialLikersMap = MutableStateFlow<Map<Long, List<User>>>(emptyMap())
    val socialLikersMap: StateFlow<Map<Long, List<User>>> = _socialLikersMap.asStateFlow()

    private val requestedTrackIds = ConcurrentHashMap.newKeySet<Long>()
    private val pendingBatchIds = ConcurrentHashMap.newKeySet<Long>()
    private var batchJob: Job? = null

    private var myUrn: String? = null

    private fun getApi(): SoundCloudApi {
        if (api == null) {
            val context = try {
                KittyTuneApp.instance.applicationContext
            } catch (e: Exception) {
                null
            }
            if (context != null) {
                api = RetrofitClient.create(context)
            }
        }
        return api ?: throw IllegalStateException("SoundCloudApi not initialized")
    }

    fun init(context: Context) {
        if (api == null) {
            api = RetrofitClient.create(context.applicationContext)
        }
    }

    fun getLikersForTrack(trackId: Long): List<User>? {
        return _socialLikersMap.value[trackId]
    }

    fun putLikersForTrack(trackId: Long, users: List<User>) {
        if (trackId <= 0) return
        requestedTrackIds.add(trackId)
        val current = _socialLikersMap.value.toMutableMap()
        current[trackId] = users
        _socialLikersMap.value = current
    }

    fun requestSocialProof(trackId: Long) {
        if (trackId <= 0) return
        if (requestedTrackIds.contains(trackId)) return
        requestSocialProof(listOf(trackId))
    }

    fun requestSocialProof(trackIds: List<Long>) {
        val newIds = trackIds.filter { it > 0 && !requestedTrackIds.contains(it) }
        if (newIds.isEmpty()) return

        newIds.forEach {
            requestedTrackIds.add(it)
            pendingBatchIds.add(it)
        }

        scheduleBatchFetch()
    }

    private fun scheduleBatchFetch() {
        synchronized(this) {
            batchJob?.cancel()
            batchJob = scope.launch {
                delay(60) // Quick debounce to group visible items
                processBatch()
            }
        }
    }

    private suspend fun processBatch() {
        val idsToFetch = pendingBatchIds.toList().take(50)
        if (idsToFetch.isEmpty()) return

        idsToFetch.forEach { pendingBatchIds.remove(it) }

        try {
            val activeApi = try {
                getApi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get API instance", e)
                idsToFetch.forEach { requestedTrackIds.remove(it) }
                return
            }

            if (myUrn == null) {
                val myId = try {
                    activeApi.getMe().id
                } catch (e: Exception) {
                    0L
                }
                myUrn = if (myId > 0) "soundcloud:users:$myId" else ""
            }

            val query = """
                query RelatedLikersForTracks(${'$'}input: AllTracksInput!) {
                    allTracks(allTracksInput: ${'$'}input) {
                       urn
                       relatedLikers {
                         users {
                           urn
                           permalink
                           username
                           avatarUrl
                           firstName
                           lastName
                           city
                           country
                           countryCode
                           tracksCount
                           playlistCount
                           followersCount
                           followingsCount
                           verified
                           isPro
                           description
                           userAvatarUrlTemplate
                           visualUrlTemplate
                           stationUrns
                           createdAt
                           badges
                         }
                       }
                    }
                }
            """.trimIndent()

            val request = RelatedLikersRequest(
                query = query,
                variables = RelatedLikersVariables(
                    input = RelatedLikersInput(
                        trackKeys = idsToFetch.map { RelatedLikersTrackKey(urn = "soundcloud:tracks:$it") }
                    )
                )
            )

            val response: RelatedLikersResponse = activeApi.getRelatedLikersGraphQL(request)
            val currentMap = _socialLikersMap.value.toMutableMap()

            val allTracksList = response.data?.allTracks
            if (allTracksList != null) {
                for (trackNode in allTracksList) {
                    val trackUrn = trackNode.urn ?: continue
                    val trackId = trackUrn.substringAfterLast(':').toLongOrNull() ?: continue
                    val rawUsers = trackNode.relatedLikers?.users.orEmpty()
                    val users = rawUsers
                        .filter { !it.urn.isNullOrEmpty() && it.urn != myUrn }
                        .mapNotNull { u ->
                            val userId = u.urn?.substringAfterLast(':')?.toLongOrNull() ?: 0L
                            if (userId > 0 && !u.username.isNullOrEmpty()) {
                                User(
                                    id = userId,
                                    username = u.username,
                                    avatarUrl = u.avatarUrl,
                                    verified = u.verified ?: false,
                                    urn = u.urn
                                )
                            } else null
                        }

                    currentMap[trackId] = users
                    Log.d(TAG, "Track $trackId has ${users.size} related likers: ${users.map { it.username }}")
                }
            } else {
                Log.w(TAG, "No allTracks returned for batch $idsToFetch. Errors: ${response.errors}")
            }

            _socialLikersMap.value = currentMap
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching social proof batch $idsToFetch", e)
            idsToFetch.forEach { requestedTrackIds.remove(it) }
        }

        if (pendingBatchIds.isNotEmpty()) {
            scheduleBatchFetch()
        }
    }
}
