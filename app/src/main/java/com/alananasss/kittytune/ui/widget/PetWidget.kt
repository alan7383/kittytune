package com.alananasss.kittytune.ui.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.ui.widget.pet.PetActivity
import com.alananasss.kittytune.ui.widget.pet.PetBrain
import com.alananasss.kittytune.ui.widget.pet.PetConfig
import com.alananasss.kittytune.ui.widget.pet.PetFacts
import com.alananasss.kittytune.ui.widget.pet.statusText
import com.alananasss.kittytune.ui.widget.pet.PetFrames
import com.alananasss.kittytune.ui.widget.pet.PetLayers
import com.alananasss.kittytune.ui.widget.pet.PetNewYear
import com.alananasss.kittytune.ui.widget.pet.PetSettings
import com.alananasss.kittytune.ui.widget.pet.PetTap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * KittyTune's home-screen cat, a tiny pixel Tamagotchi fed with music.
 *
 * - Music playing: headphones on, dancing to the beat (BPM from the automix analysis when known).
 * - Recently fed: it lives its life, rolling an activity for the time of day and its personality,
 *   with the odd random event and, very rarely, a rare moment that goes into the collection.
 * - Hungry for music: sad in a rainy room, then fast asleep with the lights off.
 * - Tap it: booped (>ω<)♡ (or whatever the tap action is set to).
 *
 * Frames animate in a launcher-side ViewFlipper; coat colours are runtime tints on white layers.
 */
class PetWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        private val KEY_PLAYING = booleanPreferencesKey("pet_playing")
        private val KEY_LAST_MUSIC_AT = longPreferencesKey("pet_last_music_at")
        private val KEY_TITLE = stringPreferencesKey("pet_title")
        private val KEY_BPM = floatPreferencesKey("pet_bpm")
        private val KEY_IDLE = stringPreferencesKey("pet_idle")
        private val KEY_DANCE = stringPreferencesKey("pet_dance")
        private val KEY_FACT_KNOWN = booleanPreferencesKey("pet_fact_known")
        private val KEY_FACT_LIKES = intPreferencesKey("pet_fact_likes")
        private val KEY_FACT_LIKED = stringPreferencesKey("pet_fact_liked")
        private val KEY_FACT_ARTIST = stringPreferencesKey("pet_fact_artist")
        private val KEY_FACT_LAST = stringPreferencesKey("pet_fact_last")
        internal val KEY_BOOP_UNTIL = longPreferencesKey("pet_boop_until")
        /** Toggled on each beat resync: switching layouts gives the launcher a fresh ViewFlipper. */
        private val KEY_ALT_LAYOUT = booleanPreferencesKey("pet_alt_layout")
        /** Which of the 4 dance poses lands on the next beat (0 = head left, on the downbeat). */
        private val KEY_BEAT_PHASE = intPreferencesKey("pet_beat_phase")
        /** Tempo heard right now (live beat tracker), 0 when unknown. */
        private val KEY_LIVE_BPM = floatPreferencesKey("pet_live_bpm")
        /** The music calmed down (intro, break, outro): the cat sways gently until the drop. */
        private val KEY_QUIET = booleanPreferencesKey("pet_quiet")
        private val KEY_TICK = longPreferencesKey("pet_tick")

        internal const val BOOP_MS = 2_600L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var lastSignature: String? = null

        /** Called on every playback state change (piggybacks on MusicWidget.update). */
        fun onPlaybackChanged(context: Context) = refresh(context.applicationContext, force = false)

        /**
         * Pushes fresh state to every pet widget. [force] = false skips it when nothing about
         * playback changed; [reroll] picks a new idle activity.
         */
        fun refresh(context: Context, force: Boolean = true, reroll: Boolean = true) {
            scope.launch {
                val appContext = context.applicationContext
                val ids = GlanceAppWidgetManager(appContext).getGlanceIds(PetWidget::class.java)
                if (ids.isEmpty()) return@launch

                val playing = try {
                    MusicManager.player.isPlaying ||
                        (MusicManager.isLoadingTrack && MusicManager.playWhenReadyRequested)
                } catch (e: Exception) {
                    false
                }
                val track = try { MusicManager.currentTrack } catch (e: Exception) { null }

                val signature = "$playing|${track?.id}"
                val wasPlaying = lastSignature?.startsWith("true") == true
                if (!force && signature == lastSignature) return@launch
                lastSignature = signature

                val now = System.currentTimeMillis()
                var lastMusicAt = PetConfig.lastMusicAt(appContext)
                if (lastMusicAt == 0L || playing || wasPlaying) {
                    lastMusicAt = now
                    PetConfig.setLastMusicAt(appContext, now)
                }

                // the automix beat analysis, only when it is confident enough to be trusted
                val beat = if (playing && track != null) {
                    withContext(Dispatchers.IO) {
                        runCatching { AppDatabase.getDatabase(appContext).beatInfoDao().getBeatInfo(track.id.toString()) }.getOrNull()
                    }?.takeIf { it.bpm in 30f..400f && it.confidence >= MIN_BEAT_CONFIDENCE }
                } else null
                val bpm = beat?.bpm ?: 0f

                val settings = PetConfig.load(appContext)
                PetNewYear.schedule(appContext)
                val dance = PetBrain.danceFor(track?.id).name
                val facts = if (settings.exclusives) withContext(Dispatchers.IO) { readFacts(appContext, track?.title) } else PetFacts()
                var rolled: PetActivity? = null

                val widget = PetWidget()
                ids.forEach { id ->
                    updateAppWidgetState(appContext, id) { prefs ->
                        prefs[KEY_PLAYING] = playing
                        prefs[KEY_LAST_MUSIC_AT] = lastMusicAt
                        prefs[KEY_TITLE] = track?.title.orEmpty()
                        prefs[KEY_BPM] = bpm
                        prefs[KEY_DANCE] = dance
                        prefs[KEY_FACT_KNOWN] = facts.known
                        prefs[KEY_FACT_LIKES] = facts.likes
                        prefs[KEY_FACT_LIKED] = facts.likedTitle.orEmpty()
                        prefs[KEY_FACT_ARTIST] = facts.topArtist.orEmpty()
                        prefs[KEY_FACT_LAST] = facts.lastTitle.orEmpty()
                        if (reroll || prefs[KEY_IDLE] == null) {
                            val pick = rolled ?: PetBrain.pickIdle(settings, avoid = PetActivity.of(prefs[KEY_IDLE]), facts = facts).also { rolled = it }
                            prefs[KEY_IDLE] = pick.name
                        }
                        prefs[KEY_TICK] = now
                    }
                    widget.update(appContext, id)
                }
                if (playing && track != null) startBeatSync(appContext, track.id, beat) else stopBeatSync()
                // A rare moment only counts when it can actually be seen (fed, not sleeping).
                rolled?.takeIf { it.isRare && !playing }?.let { PetConfig.addRareSeen(appContext, it) }
            }
        }

        private const val MIN_BEAT_CONFIDENCE = 0.3f
        /** Time between a widget update and the launcher showing it (roughly). */
        private const val UPDATE_LATENCY_MS = 90L
        /** Resync at least this often; sooner when the tempo or the energy changes. */
        private const val RESYNC_EVERY_MS = 8_000L
        private const val CHECK_EVERY_MS = 400L
        private var beatJob: kotlinx.coroutines.Job? = null
        private var beatTrackId: Long? = null

        private fun stopBeatSync() {
            beatJob?.cancel(); beatJob = null; beatTrackId = null
        }

        /**
         * Keeps the dance on the music actually playing. The launcher's ViewFlipper only knows a
         * fixed interval, starts whenever an update lands and drifts, so while music plays this
         * restarts the animation right on a beat (alternating layouts so the launcher builds a fresh
         * flipper): every few seconds, and immediately when the live tempo changes or the music
         * calms down / comes back (the drop!).
         *
         * Beats come from [com.alananasss.kittytune.audio.beat.LiveBeatTracker], which listens to
         * the decoded audio in real time and keeps its grid in the player's own timeline. The
         * automix analysis ([offline]) is only a fallback for the first seconds of a track.
         */
        private fun startBeatSync(context: Context, trackId: Long, offline: com.alananasss.kittytune.data.local.BeatInfoEntity?) {
            if (beatJob?.isActive == true && beatTrackId == trackId) return
            stopBeatSync()
            beatTrackId = trackId
            beatJob = scope.launch {
                var lastSyncAt = 0L
                var lastBpm = -1f
                var lastQuiet: Boolean? = null
                var beatCounter = 0L
                while (true) {
                    val stillPlaying = runCatching { MusicManager.player.isPlaying && MusicManager.currentTrack?.id == trackId }.getOrDefault(false)
                    if (!stillPlaying) break
                    val pos = runCatching { MusicManager.player.currentPosition }.getOrDefault(0L)
                    val live = MusicManager.liveBeat
                    val snap = live.snapshot
                    val liveNext = live.nextBeat(pos, minAheadMs = UPDATE_LATENCY_MS + 20)
                    val quiet = snap?.quiet == true

                    // where is the next beat, and at what tempo?
                    var waitMs: Long? = null
                    var bpm = 0f
                    if (liveNext != null && snap != null) {
                        waitMs = liveNext.first - UPDATE_LATENCY_MS
                        beatCounter = liveNext.second
                        bpm = snap.bpm
                    } else if (offline != null) {
                        val periodMs = 60_000.0 / offline.bpm
                        val anchor = (if (offline.downbeatConfidence > 0f) offline.downbeatOffsetMs else offline.firstBeatOffsetMs).toDouble()
                        val target = pos + UPDATE_LATENCY_MS + 20.0
                        val k = kotlin.math.ceil((target - anchor) / periodMs)
                        waitMs = (anchor + k * periodMs - pos - UPDATE_LATENCY_MS).toLong()
                        beatCounter = k.toLong()
                        bpm = offline.bpm
                    }

                    val now = System.currentTimeMillis()
                    val tempoChanged = lastBpm > 0f && bpm > 0f && kotlin.math.abs(bpm / lastBpm - 1f) > 0.04f
                    val energyChanged = lastQuiet != null && lastQuiet != quiet
                    val due = now - lastSyncAt >= RESYNC_EVERY_MS
                    if (waitMs != null && (lastSyncAt == 0L || due || tempoChanged || energyChanged)) {
                        delay(waitMs.coerceAtLeast(0))
                        val ids = GlanceAppWidgetManager(context).getGlanceIds(PetWidget::class.java)
                        if (ids.isEmpty()) break
                        val phase = (((beatCounter % 4) + 4) % 4).toInt()
                        ids.forEach { id ->
                            updateAppWidgetState(context, id) { prefs ->
                                prefs[KEY_ALT_LAYOUT] = !(prefs[KEY_ALT_LAYOUT] ?: false)
                                prefs[KEY_BEAT_PHASE] = phase
                                prefs[KEY_LIVE_BPM] = bpm
                                prefs[KEY_QUIET] = quiet
                                prefs[KEY_TICK] = System.currentTimeMillis()
                            }
                            PetWidget().update(context, id)
                        }
                        lastSyncAt = System.currentTimeMillis()
                        lastBpm = bpm
                        lastQuiet = quiet
                    }
                    delay(CHECK_EVERY_MS)
                }
            }
        }

        /** Likes, a random liked song, this month's top artist and the last song played. */
        private suspend fun readFacts(context: Context, currentTitle: String?): PetFacts = try {
            val likes = if (LikeRepository.isReady) LikeRepository.likedTracks.value else null
            val topArtist = runCatching {
                ListeningStatsRepository.init(context)
                ListeningStatsRepository.getTopArtists(System.currentTimeMillis() - 30L * 24 * 3_600_000, 1)
                    .firstOrNull()?.artistName
            }.getOrNull()
            val last = currentTitle ?: PetConfig.lastTitle(context)
            if (!currentTitle.isNullOrBlank()) PetConfig.setLastTitle(context, currentTitle)
            PetFacts(
                known = likes != null,
                likes = likes?.size ?: 0,
                likedTitle = likes?.randomOrNull()?.title,
                topArtist = topArtist?.takeIf { it.isNotBlank() },
                lastTitle = last?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            PetFacts()
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { GlanceTheme { PetContent() } }
    }

    @Composable
    private fun PetContent() {
        val context = LocalContext.current
        val prefs = currentState<Preferences>()
        val size = LocalSize.current
        val now = System.currentTimeMillis()
        val settings = PetConfig.load(context)

        val playing = prefs[KEY_PLAYING] ?: false
        val lastMusicAt = prefs[KEY_LAST_MUSIC_AT] ?: now
        val boopUntil = prefs[KEY_BOOP_UNTIL] ?: 0L
        val title = prefs[KEY_TITLE].orEmpty()
        val bpm = prefs[KEY_BPM] ?: 0f
        val idle = PetActivity.of(prefs[KEY_IDLE]) ?: PetActivity.IDLE
        val dance = PetActivity.of(prefs[KEY_DANCE]) ?: PetActivity.DANCE_BOB
        val facts = PetFacts(
            known = prefs[KEY_FACT_KNOWN] ?: false,
            likes = prefs[KEY_FACT_LIKES] ?: 0,
            likedTitle = prefs[KEY_FACT_LIKED]?.takeIf { it.isNotBlank() },
            topArtist = prefs[KEY_FACT_ARTIST]?.takeIf { it.isNotBlank() },
            lastTitle = prefs[KEY_FACT_LAST]?.takeIf { it.isNotBlank() },
        )

        val resolved = PetBrain.resolve(playing, lastMusicAt, boopUntil, now, idle, dance, settings)
        val liveBpm = prefs[KEY_LIVE_BPM] ?: 0f
        val quiet = prefs[KEY_QUIET] ?: false
        // the music calmed down (intro, break): gentle sway until the drop
        val activity = if (resolved.first.isDance && quiet) PetActivity.DANCE_SWAY else resolved.first
        val room = resolved.second
        val shownBpm = if (liveBpm > 0f) liveBpm else bpm
        val hearts = PetBrain.hearts(playing, lastMusicAt, now, settings)
        val wall = PetLayers.wallColor(room, settings)
        val dark = wall?.let { PetLayers.isDark(it) } ?: false

        val ink: ColorProvider = when {
            wall == null -> GlanceTheme.colors.onSecondaryContainer
            dark -> ColorProvider(Color(0xFFF1ECFF))
            else -> ColorProvider(Color(0xFF4A3448))
        }
        val bg: ColorProvider = wall?.let { ColorProvider(Color(it)) } ?: GlanceTheme.colors.secondaryContainer
        val pillBg = ColorProvider(if (dark) Color(0x26FFFFFF) else Color(0xB3FFFFFF))
        val heartColor = ColorProvider(if (dark) Color(0xFFFFB3CF) else Color(0xFFFF4F8B))
        val mono = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

        val compact = size.height < 150.dp
        val narrow = size.width < 140.dp
        val tapAction = when (settings.tap) {
            PetTap.BOOP -> actionRunCallback<PetBoopAction>()
            PetTap.OPEN_APP -> actionRunCallback<OpenAppAction>()
            PetTap.PLAY_PAUSE -> actionRunCallback<PlayPauseAction>()
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_pet_shape), ContentScale.FillBounds, ColorFilter.tint(bg))
                .cornerRadius(24.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize().padding(10.dp)) {
                if (settings.showName || (settings.showHearts && !narrow)) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (settings.showName) {
                            Box(
                                modifier = GlanceModifier
                                    .background(pillBg)
                                    .cornerRadius(8.dp)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(text = settings.name, maxLines = 1, style = mono)
                            }
                        }
                        if (settings.showHearts && !narrow) {
                            Spacer(GlanceModifier.defaultWeight())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { i ->
                                    Image(
                                        provider = ImageProvider(if (i < hearts) R.drawable.widget_pet_heart_full else R.drawable.widget_pet_heart_empty),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(heartColor),
                                        modifier = GlanceModifier.size(13.dp).padding(horizontal = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .clickable(tapAction),
                    contentAlignment = Alignment.Center
                ) {
                    val frameMs = when {
                        activity == PetActivity.NY_COUNTDOWN -> 1000 // one number per second, on time
                        activity.isDance && quiet -> PetBrain.frameMsForBpm(shownBpm) * 2 // half time, calm
                        activity.isDance -> PetBrain.frameMsForBpm(shownBpm)
                        else -> (activity.frameMs * settings.speed.factor).toInt()
                    }
                    AndroidRemoteViews(
                        remoteViews = petScene(
                            context, activity, settings, room, frameMs,
                            altLayout = prefs[KEY_ALT_LAYOUT] ?: false,
                            beatPhase = if (activity.isDance) prefs[KEY_BEAT_PHASE] ?: 0 else 0,
                        ),
                        modifier = GlanceModifier.fillMaxSize()
                    )
                }

                if (!compact && settings.showStatus) {
                    Spacer(GlanceModifier.height(4.dp))
                    val status = if (activity.isDance) {
                        val song = title.ifBlank { context.getString(activity.status) }
                        if (shownBpm in 30f..400f) "♪ $song · ${shownBpm.roundToInt()} BPM" else "♪ $song"
                    } else {
                        "${activity.kaomoji} ${activity.statusText(context, facts)}"
                    }
                    val midnight = if (activity == PetActivity.NY_EVE) PetNewYear.nextMidnight(now)?.takeIf { PetNewYear.showLiveClock(it, now) } else null
                    val action = if (activity.isDance) actionRunCallback<OpenAppAction>() else actionRunCallback<PlayPauseAction>()
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(pillBg)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .clickable(action),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activity.isRare) {
                                Image(
                                    provider = ImageProvider(R.drawable.widget_pet_sparkle),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(ColorProvider(Color(0xFFFFC83D))),
                                    modifier = GlanceModifier.size(14.dp).padding(end = 4.dp)
                                )
                            }
                            if (midnight != null) {
                                // ticks by itself in the launcher, down to the second
                                AndroidRemoteViews(remoteViews = countdownPill(context, midnight, now, if (dark) 0xFFF1ECFF.toInt() else 0xFF4A3448.toInt()))
                            } else {
                                Text(text = status, maxLines = 1, style = mono.copy(textAlign = TextAlign.Center))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun countdownPill(context: Context, midnight: Long, now: Long, color: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_pet_countdown)
        val base = android.os.SystemClock.elapsedRealtime() + (midnight - now)
        val year = PetNewYear.celebratedYear(now)
        views.setChronometer(R.id.pet_countdown, base, context.getString(R.string.pet_ny_chrono, year, "%s"), true)
        views.setChronometerCountDown(R.id.pet_countdown, true)
        views.setTextColor(R.id.pet_countdown, color)
        return views
    }

    private fun petScene(
        context: Context, activity: PetActivity, settings: PetSettings, room: String, frameMs: Int,
        altLayout: Boolean = false, beatPhase: Int = 0,
    ): RemoteViews {
        // The launcher reuses a ViewFlipper across updates and keeps its own flip clock. For the
        // countdown each batch gets the other (identical) layout, so a fresh flipper starts on time.
        // dances start on the pose that matches the beat in the bar (frames are 4 poses, twice)
        val base = PetBrain.framesFor(activity)
        val frames = if (activity.isDance && beatPhase != 0) base.drop(beatPhase) + base.take(beatPhase) else base
        val firstNumber = frames.first().removePrefix("count_").toIntOrNull()
        val layout = when {
            // 1st countdown batch (10..3); the eve before and the 2nd batch use the other one
            activity == PetActivity.NY_COUNTDOWN -> if (firstNumber != null && firstNumber >= 4) R.layout.widget_pet_flipper_b else R.layout.widget_pet_flipper
            altLayout -> R.layout.widget_pet_flipper_b
            else -> R.layout.widget_pet_flipper
        }
        val views = RemoteViews(context.packageName, layout)
        views.setImageViewResource(R.id.pet_room, if (settings.roomDecor) PetFrames.rooms.getValue(room) else 0)
        val decor = PetBrain.decorFor(context, settings)?.let { PetFrames.decor[it] } ?: 0
        views.setImageViewResource(R.id.pet_decor, if (settings.roomDecor && room != "space") decor else 0)
        frames.take(8).forEachIndexed { i, frame ->
            PetLayers.of(context, frame, settings, bitmapScale = 4, alphaOnly = true).forEachIndexed { l, layer ->
                val id = LAYER_IDS[i][l]
                if (layer.bitmap != null) views.setImageViewBitmap(id, layer.bitmap) else views.setImageViewResource(id, layer.res)
                if (layer.tint != null) views.setInt(id, "setColorFilter", layer.tint)
            }
        }
        views.setInt(R.id.pet_flipper, "setFlipInterval", frameMs)
        // every update starts the animation from its first frame (matters for the countdown)
        views.setDisplayedChild(R.id.pet_flipper, 0)
        return views
    }
}

private val LAYER_IDS = arrayOf(
    intArrayOf(R.id.pet_l1_0, R.id.pet_l1_1, R.id.pet_l1_2, R.id.pet_l1_3, R.id.pet_l1_4, R.id.pet_l1_5, R.id.pet_l1_6, R.id.pet_l1_7),
    intArrayOf(R.id.pet_l2_0, R.id.pet_l2_1, R.id.pet_l2_2, R.id.pet_l2_3, R.id.pet_l2_4, R.id.pet_l2_5, R.id.pet_l2_6, R.id.pet_l2_7),
    intArrayOf(R.id.pet_l3_0, R.id.pet_l3_1, R.id.pet_l3_2, R.id.pet_l3_3, R.id.pet_l3_4, R.id.pet_l3_5, R.id.pet_l3_6, R.id.pet_l3_7),
    intArrayOf(R.id.pet_l4_0, R.id.pet_l4_1, R.id.pet_l4_2, R.id.pet_l4_3, R.id.pet_l4_4, R.id.pet_l4_5, R.id.pet_l4_6, R.id.pet_l4_7),
    intArrayOf(R.id.pet_l5_0, R.id.pet_l5_1, R.id.pet_l5_2, R.id.pet_l5_3, R.id.pet_l5_4, R.id.pet_l5_5, R.id.pet_l5_6, R.id.pet_l5_7),
    intArrayOf(R.id.pet_l6_0, R.id.pet_l6_1, R.id.pet_l6_2, R.id.pet_l6_3, R.id.pet_l6_4, R.id.pet_l6_5, R.id.pet_l6_6, R.id.pet_l6_7),
    intArrayOf(R.id.pet_l7_0, R.id.pet_l7_1, R.id.pet_l7_2, R.id.pet_l7_3, R.id.pet_l7_4, R.id.pet_l7_5, R.id.pet_l7_6, R.id.pet_l7_7),
    intArrayOf(R.id.pet_l8_0, R.id.pet_l8_1, R.id.pet_l8_2, R.id.pet_l8_3, R.id.pet_l8_4, R.id.pet_l8_5, R.id.pet_l8_6, R.id.pet_l8_7),
)

/** Boop the kitty: a few seconds of (>ω<)♡, then it wanders off to do something else. */
class PetBoopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PetConfig.addBoop(context)
        val until = System.currentTimeMillis() + PetWidget.BOOP_MS
        updateAppWidgetState(context, glanceId) { it[PetWidget.KEY_BOOP_UNTIL] = until }
        PetWidget().update(context, glanceId)
        delay(PetWidget.BOOP_MS + 100L)
        PetWidget.refresh(context, force = true, reroll = true)
    }
}
