    package com.alananasss.kittytune.ui.profile

    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableIntStateOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.ListeningStatsRepository
    import com.alananasss.kittytune.data.local.TopArtistResult
    import com.alananasss.kittytune.data.local.TopTrackResult
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import java.util.Calendar

    data class PeriodStats(
        val totalListenTimeMs: Long = 0,
        val totalEvents: Int = 0,
        val completedSongs: Int = 0,
        val skippedNext: Int = 0,
        val skippedPrevious: Int = 0,
        val manualReplays: Int = 0,
        val repeatOneLoops: Int = 0,
        val uniqueTracks: Int = 0,
        val uniqueArtists: Int = 0,
        val topTracks: List<TopTrackResult> = emptyList(),
        val topArtists: List<TopArtistResult> = emptyList()
    ) {
        val totalPlays: Int get() = completedSongs + manualReplays + repeatOneLoops
        val totalSkips: Int get() = skippedNext + skippedPrevious
        val skipRate: Float get() = if (totalEvents > 0) totalSkips.toFloat() / totalEvents else 0f
        val completionRate: Float get() = if (totalEvents > 0) totalPlays.toFloat() / totalEvents else 0f
    }

    data class TimelineChunk(
        val startDateMs: Long,
        val endDateMs: Long,
        val topTrack: TopTrackResult?,
        val topArtist: TopArtistResult?
    )

    enum class StatsPeriod { WEEK, MONTH, ALL_TIME }

    class ListeningStatsViewModel(application: Application) : AndroidViewModel(application) {

        var selectedPeriod by mutableStateOf(StatsPeriod.WEEK)
            private set
        var stats by mutableStateOf(PeriodStats())
            private set
        var isLoading by mutableStateOf(true)
            private set

        var timelineChunks = mutableStateOf<List<TimelineChunk>>(emptyList())
            private set
        var isTimelineLoading by mutableStateOf(false)
            private set
        var timelineHasMore by mutableStateOf(true)
            private set

        private var currentTimelineOffsetMonths: Int = 0

        init {
            loadStats()
        }

        fun selectPeriod(period: StatsPeriod) {
            if (period == selectedPeriod) return
            selectedPeriod = period
            loadStats()
            if (period == StatsPeriod.ALL_TIME) {
                resetAndLoadTimeline()
            }
        }

        fun refreshStats() {
            loadStats()
            if (selectedPeriod == StatsPeriod.ALL_TIME) {
                resetAndLoadTimeline()
            }
        }

        private fun loadStats() {
            isLoading = true
            viewModelScope.launch {
                val since = getSinceTimestamp(selectedPeriod)
                val newStats = withContext(Dispatchers.IO) {
                    val totalListenTime = ListeningStatsRepository.getTotalListenTime(since)
                    val totalEvents = ListeningStatsRepository.getTotalEvents(since)
                    val completed = ListeningStatsRepository.getEventCount("PLAY_COMPLETE", since)
                    val skipNext = ListeningStatsRepository.getEventCount("SKIP_NEXT", since)
                    val skipPrev = ListeningStatsRepository.getEventCount("SKIP_PREVIOUS", since)
                    val replays = ListeningStatsRepository.getEventCount("MANUAL_REPLAY", since)
                    val repeatLoops = ListeningStatsRepository.getEventCount("REPEAT_ONE_LOOP", since)
                    val uniqueTracks = ListeningStatsRepository.getUniqueTracks(since)
                    val uniqueArtists = ListeningStatsRepository.getUniqueArtists(since)
                    val topTracks = ListeningStatsRepository.getTopTracks(since, 10)
                    val topArtists = ListeningStatsRepository.getTopArtists(since, 10)

                    PeriodStats(
                        totalListenTimeMs = totalListenTime,
                        totalEvents = totalEvents,
                        completedSongs = completed,
                        skippedNext = skipNext,
                        skippedPrevious = skipPrev,
                        manualReplays = replays,
                        repeatOneLoops = repeatLoops,
                        uniqueTracks = uniqueTracks,
                        uniqueArtists = uniqueArtists,
                        topTracks = topTracks,
                        topArtists = topArtists
                    )
                }
                stats = newStats
                isLoading = false
            }
        }

        private fun getSinceTimestamp(period: StatsPeriod): Long {
            val cal = Calendar.getInstance()
            return when (period) {
                StatsPeriod.WEEK -> {
                    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                StatsPeriod.MONTH -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                StatsPeriod.ALL_TIME -> 0L
            }
        }

        private fun resetAndLoadTimeline() {
            timelineChunks.value = emptyList()
            currentTimelineOffsetMonths = 0
            timelineHasMore = true
            loadNextTimelineChunk()
        }

        fun loadNextTimelineChunk() {
            if (isTimelineLoading || !timelineHasMore) return
            isTimelineLoading = true

            viewModelScope.launch {
                val chunks = withContext(Dispatchers.IO) {
                    val newChunks = mutableListOf<TimelineChunk>()
                    
                    // Load 3 months per request
                    for (i in 0 until 3) {
                        val calEnd = Calendar.getInstance().apply {
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            add(Calendar.MONTH, -currentTimelineOffsetMonths)
                        }
                        val untilMs = if (currentTimelineOffsetMonths == 0) System.currentTimeMillis() else calEnd.timeInMillis

                        val calStart = calEnd.clone() as Calendar
                        calStart.add(Calendar.MONTH, -1)
                        val sinceMs = calStart.timeInMillis

                        val topTrack = ListeningStatsRepository.getTopTracksBetween(sinceMs, untilMs, 1).firstOrNull()
                        val topArtist = ListeningStatsRepository.getTopArtistsBetween(sinceMs, untilMs, 1).firstOrNull()

                        if (topTrack != null || topArtist != null) {
                            newChunks.add(TimelineChunk(sinceMs, untilMs, topTrack, topArtist))
                        }

                        currentTimelineOffsetMonths++
                        
                        val totalBefore = ListeningStatsRepository.getTotalEvents(0)
                        val eventsAfterStart = ListeningStatsRepository.getTotalEvents(sinceMs)
                        if (eventsAfterStart >= totalBefore) {
                            timelineHasMore = false
                            break
                        }
                    }
                    newChunks
                }

                if (chunks.isEmpty() && timelineHasMore) {
                    isTimelineLoading = false
                    loadNextTimelineChunk()
                } else {
                    timelineChunks.value = timelineChunks.value + chunks
                    isTimelineLoading = false
                }
            }
        }
    }
