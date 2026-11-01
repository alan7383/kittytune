package com.alananasss.kittytune.ui.profile.passport

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.AchievementManager
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.MusicPassport
import com.alananasss.kittytune.data.stats.ReportPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.Calendar

/**
 * Builds the listener's [MusicPassport] from their whole listening history.
 *
 * Mirrors the recaps: it reads the local stats once, reacts to new listens, and produces one
 * ready-to-draw object. The holder's name and avatar are a best-effort nicety — the passport is
 * complete without them, so a guest or an offline account still gets one.
 */
class MusicPassportViewModel(application: Application) : AndroidViewModel(application) {

    var passport by mutableStateOf<MusicPassport?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    private val splitter = ListeningReports.ArtistSplitter { raw ->
        ListeningStatsRepository.splitArtistNames(raw)
    }

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { ListeningStatsRepository.revision.drop(1).collect { load() } }
    }

    fun refresh() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            isLoading = true
            val context = getApplication<Application>()
            val prefs = PlayerPreferences(context)
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val weekStartsOn = firstDayOfWeekAsDayOfWeek()

            val firstEventMs = ListeningStatsRepository.getOldestEventAt()
            val window = ListeningReports.windowFor(
                ReportPeriod.ALL_TIME, now, zone, firstEventMs, weekStartsOn, 0
            )
            val rows = ListeningStatsRepository.getRecentEventsBetween(
                window.startMs, window.endMs, EVENT_LIMIT
            )
            val report = ListeningReports.build(
                period = ReportPeriod.ALL_TIME,
                window = window,
                events = rows,
                previousListenMs = null,
                zone = zone,
                splitter = splitter,
                nowMs = now,
            )

            // Citizenships: the genres the listener belongs to, by time spent.
            val citizenships = com.alananasss.kittytune.data.stats.ListeningRecaps
                .genres(rows, limit = 4)
                .map {
                    MusicPassport.Citizenship(
                        genre = it.name,
                        sharePercent = (it.share * 100f).toInt().coerceIn(0, 100),
                    )
                }

            // Visas: where the listener's finds came from, counted as the distinct tracks each
            // platform contributed, dated from the first listen there. Named for people, not slugs.
            val visas = rows
                .groupBy { it.source.lowercase() }
                .mapNotNull { (source, events) ->
                    val label = platformLabel(source) ?: return@mapNotNull null
                    MusicPassport.Visa(
                        platform = label,
                        discoveries = events.map { it.trackId }.toSet().size,
                        since = stampDate(events.minOf { it.timestamp }, zone),
                    )
                }
                .sortedByDescending { it.discoveries }

            // Stamps: achievements earned, the most valuable first, dated like entry stamps.
            val progress = AchievementManager.progressFlow.value
            val stamps = AchievementManager.definitions
                .filter { progress[it.id]?.isUnlocked == true }
                .sortedByDescending { it.xpReward }
                .take(MAX_STAMPS)
                .map { def ->
                    val unlockedAt = progress[def.id]?.unlockedAt ?: 0L
                    MusicPassport.Stamp(
                        label = context.getString(def.titleResId).uppercase(),
                        date = if (unlockedAt > 0L) stampDate(unlockedAt, zone) else "",
                    )
                }

            val holderName = prefs.getCachedUsername()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.passport_default_holder)

            passport = MusicPassport(
                holderName = holderName,
                passportNo = passportNumber(prefs.getCachedUserId(), firstEventMs ?: now, report.plays),
                issuedAtMs = firstEventMs ?: now,
                avatarUrl = null,
                citizenships = citizenships,
                visas = visas,
                stamps = stamps,
                totalMinutes = report.totalListenMs / 60_000L,
                tracks = report.uniqueTracks,
                artists = report.uniqueArtists,
                longestStreakDays = report.longestStreakDays,
                travellerClass = context.getString(travellerClassRes(report)),
                accentArgb = DEFAULT_ACCENT,
            )
            isLoading = false

            // The avatar is the one thing that needs the network; fill it in once the card is
            // already up, and only if it arrives.
            launch { fetchAvatar() }
        }
    }

    private suspend fun fetchAvatar() {
        val context = getApplication<Application>()
        if (com.alananasss.kittytune.data.TokenManager(context).isGuestMode()) return
        val avatar = withContext(Dispatchers.IO) {
            runCatching { RetrofitClient.create(context).getMe().avatarUrl }.getOrNull()
        } ?: return
        passport = passport?.copy(avatarUrl = avatar)
    }

    private fun firstDayOfWeekAsDayOfWeek(): DayOfWeek = when (Calendar.getInstance().firstDayOfWeek) {
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.MONDAY
    }

    /** The platform's display name, or null for one that is not a discovery source of its own. */
    private fun platformLabel(source: String): String? = when (source) {
        "soundcloud" -> "SoundCloud"
        "youtube" -> "YouTube"
        "spotify" -> "Spotify"
        "tidal" -> "Tidal"
        "deezer" -> "Deezer"
        "qobuz" -> "Qobuz"
        "local" -> "Local"
        else -> null
    }

    /** A short traveller archetype, from when and how widely the listener listens. */
    private fun travellerClassRes(report: ListeningReport): Int {
        val total = report.totalListenMs.coerceAtLeast(1L)
        val night = (0..4).sumOf { report.hours[it] }.toFloat() / total
        val morning = (5..8).sumOf { report.hours[it] }.toFloat() / total
        val replay = if (report.uniqueTracks == 0) 0f else report.plays.toFloat() / report.uniqueTracks
        return when {
            night >= 0.25f -> R.string.passport_class_night
            morning >= 0.30f -> R.string.passport_class_dawn
            report.uniqueArtists >= 150 -> R.string.passport_class_explorer
            replay >= 2.2f -> R.string.passport_class_devoted
            else -> R.string.passport_class_wanderer
        }
    }

    /** "31 OCT 2026": the way a border stamp dates an entry, whatever the device's language. */
    private fun stampDate(ms: Long, zone: ZoneId): String =
        java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)
            .format(java.time.Instant.ofEpochMilli(ms).atZone(zone))
            .uppercase(java.util.Locale.ENGLISH)

    /** A stable, passport-looking number from the account and when its listening began. */
    private fun passportNumber(userId: Long, firstEventMs: Long, plays: Int): String {
        val year = java.time.Instant.ofEpochMilli(firstEventMs).atZone(ZoneId.systemDefault()).year
        val seed = (userId * 31 + firstEventMs / 86_400_000L + plays).let {
            (it % 1_000_000L + 1_000_000L) % 1_000_000L
        }
        return "KT·%d·%06d".format(year, seed)
    }

    private companion object {
        const val EVENT_LIMIT = 60_000
        const val MAX_STAMPS = 4
        /** A violet that matches the app, used until the screen themes the card. */
        const val DEFAULT_ACCENT = 0xFF8E6BFF.toInt()
    }
}
