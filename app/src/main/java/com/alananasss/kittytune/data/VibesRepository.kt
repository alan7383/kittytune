package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.GraphQlLikesCollectionsRequest
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.Vibe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VibesRepository {
    private const val TAG = "VibesRepository"
    private var api: SoundCloudApi? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _vibes = MutableStateFlow<List<Vibe>>(emptyList())
    val vibes: StateFlow<List<Vibe>> = _vibes.asStateFlow()

    private var lastFetchTime = 0L

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

    fun loadVibes(likedTracks: List<Track> = emptyList()) {
        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 15_000L && _vibes.value.isNotEmpty()) {
            return
        }

        scope.launch {
            try {
                val activeApi = try {
                    getApi()
                } catch (e: Exception) {
                    null
                }

                var fetchedVibes: List<Vibe> = emptyList()

                if (activeApi != null) {
                    try {
                        val response = activeApi.getMyLikesCollectionsGraphQL(GraphQlLikesCollectionsRequest())
                        val collections = response.data?.myLikesCollections?.collections.orEmpty()
                        if (collections.isNotEmpty()) {
                            fetchedVibes = collections.mapNotNull { col ->
                                val id = col.id ?: return@mapNotNull null
                                val name = col.displayName ?: id
                                val trackIds = col.tracks?.mapNotNull {
                                    it.urn?.substringAfterLast(':')?.toLongOrNull()
                                }?.toSet() ?: emptySet()

                                Vibe(
                                    id = id,
                                    displayName = name,
                                    color = col.color,
                                    size = col.size ?: trackIds.size,
                                    trackIds = trackIds
                                )
                            }.filter { it.size > 0 || it.trackIds.isNotEmpty() }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch GraphQL vibes: ${e.message}")
                    }
                }

                if (fetchedVibes.isNotEmpty()) {
                    _vibes.value = fetchedVibes
                    lastFetchTime = System.currentTimeMillis()
                    Log.d(TAG, "Loaded ${fetchedVibes.size} vibes from SoundCloud GraphQL")
                } else if (likedTracks.isNotEmpty()) {
                    val localVibes = computeLocalVibes(likedTracks)
                    _vibes.value = localVibes
                    lastFetchTime = System.currentTimeMillis()
                    Log.d(TAG, "Computed ${localVibes.size} local vibes from liked tracks")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadVibes", e)
                if (likedTracks.isNotEmpty()) {
                    _vibes.value = computeLocalVibes(likedTracks)
                }
            }
        }
    }

    /**
     * Compute default SoundCloud vibes locally if GraphQL is not available or empty.
     */
    private fun computeLocalVibes(tracks: List<Track>): List<Vibe> {
        val appContext = try {
            KittyTuneApp.instance.applicationContext
        } catch (e: Exception) {
            null
        }

        fun getString(resId: Int, fallback: String): String {
            return appContext?.getString(resId) ?: fallback
        }

        val vibeDefinitions = listOf(
            Triple(
                "hiphop",
                getString(R.string.vibe_hiphop, "Hip-Hop"),
                listOf("hip hop", "hip-hop", "hiphop", "rap", "trap", "drill", "boom bap")
            ),
            Triple(
                "electronic",
                getString(R.string.vibe_electronic, "Electronic"),
                listOf(
                    "electronic",
                    "dance & edm",
                    "house",
                    "techno",
                    "electro",
                    "trance",
                    "dubstep",
                    "drum & bass",
                    "dnb",
                    "bass"
                )
            ),
            Triple(
                "pop",
                getString(R.string.vibe_pop, "Pop"),
                listOf("pop", "indie pop", "synthpop", "electropop", "k-pop", "j-pop")
            ),
            Triple(
                "rnb",
                getString(R.string.vibe_rnb, "R&B"),
                listOf("r&b", "rnb", "soul", "neo-soul", "urban", "funk")
            ),
            Triple(
                "rock",
                getString(R.string.vibe_rock, "Rock"),
                listOf("rock", "alternative rock", "indie rock", "metal", "punk", "grunge", "hard rock")
            ),
            Triple(
                "chill",
                getString(R.string.vibe_chill, "Chill"),
                listOf("chill", "chillout", "ambient", "lo-fi", "lofi", "downtempo", "relax", "acoustic")
            ),
            Triple(
                "dancy",
                getString(R.string.vibe_dancy, "Dance"),
                listOf("dance", "club", "disco", "afrobeats", "reggaeton", "latin", "dancehall")
            ),
            Triple(
                "euphoric",
                getString(R.string.vibe_euphoric, "Euphoric"),
                listOf("future bass", "hyperpop", "melodic", "euphoric", "edm", "progressive")
            ),
            Triple(
                "happy",
                getString(R.string.vibe_happy, "Happy"),
                listOf("happy", "sunshine", "funky", "feel good", "upbeat", "summer")
            ),
            Triple(
                "majestic",
                getString(R.string.vibe_majestic, "Majestic"),
                listOf("cinematic", "orchestral", "soundtrack", "epic", "classical", "piano")
            )
        )

        val result = mutableListOf<Vibe>()

        for ((id, displayName, keywords) in vibeDefinitions) {
            val matchingTrackIds = tracks.filter { track ->
                val genre = track.genre.orEmpty().lowercase()
                val tagList = track.tagList.orEmpty().lowercase()
                val title = track.title.orEmpty().lowercase()
                keywords.any { kw ->
                    genre.contains(kw) || tagList.contains(kw) || title.contains(kw)
                }
            }.map { it.id }.toSet()

            if (matchingTrackIds.isNotEmpty()) {
                result.add(
                    Vibe(
                        id = id,
                        displayName = displayName,
                        size = matchingTrackIds.size,
                        trackIds = matchingTrackIds
                    )
                )
            }
        }

        return result
    }
}
