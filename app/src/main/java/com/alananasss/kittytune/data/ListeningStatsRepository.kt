    package com.alananasss.kittytune.data

    import android.content.Context
    import com.alananasss.kittytune.data.local.AppDatabase
    import com.alananasss.kittytune.data.local.ListeningStatsEvent
    import com.alananasss.kittytune.data.local.TopArtistResult
    import com.alananasss.kittytune.data.local.TopTrackResult
    import com.alananasss.kittytune.domain.Track
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch

    object ListeningStatsRepository {
        private lateinit var database: AppDatabase
        private val scope = CoroutineScope(Dispatchers.IO)

        fun init(context: Context) {
            database = AppDatabase.getDatabase(context)
        }

        fun recordEvent(
            track: Track,
            eventType: String,
            listenDurationMs: Long = 0
        ) {
            scope.launch {
                val event = ListeningStatsEvent(
                    trackId = track.id,
                    trackTitle = track.title ?: "Unknown",
                    artistName = track.user?.username ?: "Unknown",
                    artistId = track.user?.id,
                    artistPermalink = track.user?.permalinkUrl,
                    artistAvatarUrl = track.user?.avatarUrl,
                    artworkUrl = track.fullResArtwork,
                    source = track.source ?: "soundcloud",
                    eventType = eventType,
                    listenDurationMs = listenDurationMs,
                    trackDurationMs = track.durationMs ?: 0L
                )
                database.downloadDao().insertStatsEvent(event)
            }
        }

        suspend fun getTopTracks(since: Long, limit: Int = 10): List<TopTrackResult> {
            return database.downloadDao().getTopTracksAfter(since, limit)
        }

        suspend fun getTopArtists(since: Long, limit: Int = 10): List<TopArtistResult> {
            return database.downloadDao().getTopArtistsAfter(since, limit)
        }

        suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult> {
            return database.downloadDao().getTopTracksBetween(since, until, limit)
        }

        suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult> {
            return database.downloadDao().getTopArtistsBetween(since, until, limit)
        }

        suspend fun getTotalListenTime(since: Long): Long {
            return database.downloadDao().getTotalListenTimeAfter(since)
        }

        suspend fun getEventCount(type: String, since: Long): Int {
            return database.downloadDao().getEventCountByType(type, since)
        }

        suspend fun getTotalEvents(since: Long): Int {
            return database.downloadDao().getTotalEventsAfter(since)
        }

        suspend fun getUniqueTracks(since: Long): Int {
            return database.downloadDao().getUniqueTracksAfter(since)
        }

        suspend fun getUniqueArtists(since: Long): Int {
            return database.downloadDao().getUniqueArtistsAfter(since)
        }

        suspend fun getEvents(since: Long): List<ListeningStatsEvent> {
            return database.downloadDao().getEventsAfter(since)
        }

        fun clearStats() {
            scope.launch {
                database.downloadDao().clearStats()
            }
        }
    }
