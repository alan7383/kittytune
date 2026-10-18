package com.alananasss.kittytune.data.together

import android.content.Context
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the shared playlist needs from the player, and what it asks of it. Implemented by the player's view model. */
interface TogetherPlayer {
    fun sharedCurrent(): SharedTrack?
    fun sharedPositionMs(): Long
    fun sharedIsPlaying(): Boolean
    fun sharedUpcoming(): List<SharedTrack>
    fun sharedAutomix(): Boolean

    /** Play [track] at [positionMs] with [upcoming] after it, or follow along if it is already playing. */
    fun followHost(track: SharedTrack, positionMs: Long, isPlaying: Boolean, upcoming: List<SharedTrack>?, automix: Boolean)

    /** The host's side of a member's request. */
    fun hostControl(action: String, positionMs: Long)

    /** The host's side of "play this next": after the song playing, after earlier requests. */
    fun hostPlayNext(track: SharedTrack)

    /** The host plays [tracks] from [index], for starting the playlist or a suggestion. */
    fun hostPlay(tracks: List<SharedTrack>, index: Int)
}

/**
 * Shared playlists, "listen together" kept as a playlist rather than a room (issue #66, ported from desktop).
 *
 * A shared playlist has a code; whoever has it can open the playlist, see who is listening, and listen along. One
 * listener is the host, the first to press play while nobody hosts, and everyone else plays what the host plays,
 * at the same moment. Anyone can pause, seek, skip or put a track next; the host applies it and everyone follows.
 * Anyone can suggest a track for the playlist; the host accepts it, listens to it, or turns it down. Only the host
 * switches automix. Volume and effects stay each listener's own.
 *
 * Every saved shared playlist is followed while the app runs, so its card shows who is listening even when this
 * listener is not, and joining starts in step at once.
 */
object Together {

    /** A shared playlist this listener has, with its tracks as last seen, so it survives nobody being online. */
    data class Saved(val code: String, val name: String, val createdAt: Long, val tracks: List<SharedTrack> = emptyList())

    data class Member(val id: String, val name: String, val isListening: Boolean, val lastSeen: Long)

    data class Suggestion(val track: SharedTrack, val byName: String, val from: String)

    /** Everything known about one shared playlist. */
    data class Room(
        val code: String,
        val name: String,
        val members: Map<String, Member> = emptyMap(),
        val hostId: String? = null,
        val hostSeenAt: Long = 0L,
        /** When the current host claimed: the latest claim wins, so two people starting at once agree on one. */
        val hostClaimedAt: Long = 0L,
        val playlist: List<SharedTrack> = emptyList(),
        val upcoming: List<SharedTrack> = emptyList(),
        val nowPlaying: SharedTrack? = null,
        val isPlaying: Boolean = false,
        val automix: Boolean = false,
        val suggestions: List<Suggestion> = emptyList(),
        val queueVersion: Int = 0,
    ) {
        val listeners: List<Member> get() = members.values.filter { it.isListening && System.currentTimeMillis() - it.lastSeen < PRESENCE_TIMEOUT_MS }
        val hasLiveHost: Boolean get() = hostId != null && System.currentTimeMillis() - hostSeenAt < HOST_TIMEOUT_MS
    }

    private const val PREFS = "together_prefs"
    private const val KEY_SAVED = "together_saved"
    private const val KEY_MEMBER_ID = "together_member_id"
    private const val KEY_NAME = "together_name"
    private const val PRESENCE_TIMEOUT_MS = 25_000L
    private const val HOST_TIMEOUT_MS = 20_000L
    private const val HELLO_EVERY_MS = 8_000L
    private const val STATE_EVERY_MS = 4_000L
    private const val SETTLE_BEFORE_STATE_MS = 150L

    /** What the quickest trip is taken to cost, as the floor itself holds only the clocks' difference. */
    private const val TYPICAL_TRIP_MS = 120L

    /** The longest a message in flight is allowed to add to the host's position. */
    private const val MAX_FLIGHT_MS = 3_000L
    private const val MAX_SHARED_UPCOMING = 30

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    private fun prefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Lazy so the context is in (Together.init) before the id is read or minted; it must survive restarts.
    val memberId: String by lazy {
        synchronized(this) {
            prefs()?.getString(KEY_MEMBER_ID, null) ?: java.util.UUID.randomUUID().toString().also {
                prefs()?.edit()?.putString(KEY_MEMBER_ID, it)?.apply()
            }
        } ?: java.util.UUID.randomUUID().toString()
    }

    var myName: String
        get() = prefs()?.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: defaultName
        set(value) = prefs()?.edit()?.putString(KEY_NAME, value.trim())?.apply() ?: Unit

    /** Filled in by the app with the signed-in account's name. */
    @Volatile
    var defaultName: String = "Listener"

    private val relay by lazy { TogetherRelay("kt-" + memberId.take(12) + "-" + System.nanoTime().toString(36).takeLast(4)) }

    // Loaded on the IO scope in start(): the saved playlists are prefs + Gson, and this object is
    // first touched from the UI thread at startup, where parsing them janked the app's first frames.
    private val _saved = MutableStateFlow<List<Saved>>(emptyList())
    val saved: StateFlow<List<Saved>> = _saved.asStateFlow()

    private val _rooms = MutableStateFlow<Map<String, Room>>(emptyMap())
    val rooms: StateFlow<Map<String, Room>> = _rooms.asStateFlow()

    /** The playlist this listener is listening along in, if any. */
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active.asStateFlow()

    val isConnected: StateFlow<Boolean> get() = relay.isConnected

    private var player: TogetherPlayer? = null
    private var started = false
    private var tickJob: Job? = null

    // queueVersion, lastSentQueueVersion and lastUpcomingIds are touched from the tick job, the
    // relay's incoming-collect and publishStateSoon coroutines — three threads on the IO pool.
    // The unguarded read-modify-write in publishQueue could interleave: two publishes could ship
    // the same version for different queues, and a follower comparing them would miss a queue
    // change (the audit's "queueVersion Together non synchronisé").
    private val queueStateLock = Any()
    private var lastSentQueueVersion = -1
    private var queueVersion = 0
    private var lastUpcomingIds: List<Long> = emptyList()

    fun isHostOf(code: String): Boolean = _rooms.value[code]?.hostId == memberId

    val activeRoom: Room? get() = _active.value?.let { _rooms.value[it] }

    /** Stash the context; must be called once from the Application before anything else. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Connects and follows every saved playlist. Called once the player exists. */
    fun start(player: TogetherPlayer) {
        this.player = player
        if (started) return
        started = true
        scope.launch {
            _saved.value = _saved.value.ifEmpty { loadSaved() }
            _saved.value.forEach { follow(it) }
            relay.incoming.collect { handle(it.code, it.message) }
        }
        tickJob = scope.launch {
            var lastHello = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now - lastHello >= HELLO_EVERY_MS) {
                    lastHello = now
                    _active.value?.let { send(it, TogetherMessage.Hello(memberId, now, myName, isListening = true)) }
                }
                _active.value?.let { code -> if (isHostOf(code)) publishState(code) }
                delay(STATE_EVERY_MS)
            }
        }
    }

    // ─── Saved playlists ──────────────────────────────────────────────────────────────────────

    fun create(name: String): Saved {
        val saved = Saved(TogetherWire.newCode(), name.trim().ifBlank { "Together" }, System.currentTimeMillis())
        addSaved(saved)
        updateRoom(saved.code) { it.copy(name = saved.name) }
        return saved
    }

    fun join(code: String): Saved? {
        val normalized = TogetherWire.normalizeCode(code)
        if (normalized.length != TogetherWire.CODE_LENGTH) return null
        _saved.value.firstOrNull { it.code == normalized }?.let { return it }
        val saved = Saved(normalized, "", System.currentTimeMillis())
        addSaved(saved)
        scope.launch { send(normalized, TogetherMessage.SyncRequest(memberId, System.currentTimeMillis())) }
        return saved
    }

    fun forget(code: String) {
        if (_active.value == code) stopListening()
        _saved.value = _saved.value.filterNot { it.code == code }
        persistSaved()
        scope.launch { relay.unfollow(code) }
        _rooms.value = _rooms.value - code
    }

    private fun addSaved(saved: Saved) {
        _saved.value = listOf(saved) + _saved.value.filterNot { it.code == saved.code }
        persistSaved()
        scope.launch { follow(saved) }
    }

    private fun follow(saved: Saved) {
        updateRoom(saved.code) { if (it.name.isBlank()) it.copy(name = saved.name) else it }
        relay.follow(saved.code)
        send(saved.code, TogetherMessage.SyncRequest(memberId, System.currentTimeMillis()))
    }

    // ─── Listening along ──────────────────────────────────────────────────────────────────────

    /**
     * Listen along in [code]: with a live host, in step with them; without one, this listener becomes the host and
     * the playlist starts from [startIndex].
     */
    fun startListening(code: String, startIndex: Int = 0) {
        _active.value = code
        val room = _rooms.value[code]
        val now = System.currentTimeMillis()
        scope.launch {
            send(code, TogetherMessage.Hello(memberId, now, myName, isListening = true))
            if (room?.hasLiveHost == true && room.hostId != memberId) {
                send(code, TogetherMessage.SyncRequest(memberId, now))
            } else {
                claimHost(code)
                val tracks = room?.playlist.orEmpty()
                if (tracks.isNotEmpty()) withContext(Dispatchers.Main) { player?.hostPlay(tracks, startIndex.coerceIn(0, tracks.lastIndex)) }
            }
        }
    }

    fun stopListening() {
        val code = _active.value ?: return
        _active.value = null
        scope.launch { send(code, TogetherMessage.Bye(memberId, System.currentTimeMillis())) }
    }

    fun claimHost(code: String) {
        val now = System.currentTimeMillis()
        updateRoom(code) { it.copy(hostId = memberId, hostSeenAt = now, hostClaimedAt = now) }
        send(code, TogetherMessage.ClaimHost(memberId, now))
        publishQueue(code, force = true)
        publishState(code)
    }

    // ─── Asking the host ──────────────────────────────────────────────────────────────────────

    fun playNext(track: SharedTrack) {
        val code = _active.value ?: return
        if (isHostOf(code)) player?.hostPlayNext(track)
        else send(code, TogetherMessage.PlayNext(memberId, System.currentTimeMillis(), track, myName))
    }

    fun suggest(code: String, track: SharedTrack) {
        if (isHostOf(code) || _rooms.value[code]?.hasLiveHost != true) {
            addToPlaylist(code, track)
        } else {
            send(code, TogetherMessage.Suggest(memberId, System.currentTimeMillis(), track, myName))
            updateRoom(code) { it.copy(suggestions = it.suggestions + Suggestion(track, myName, memberId)) }
        }
    }

    fun acceptSuggestion(code: String, suggestion: Suggestion) {
        addToPlaylist(code, suggestion.track)
        resolveSuggestion(code, suggestion)
    }

    fun declineSuggestion(code: String, suggestion: Suggestion) = resolveSuggestion(code, suggestion)

    fun listenToSuggestion(code: String, suggestion: Suggestion) {
        if (isHostOf(code)) player?.hostPlay(listOf(suggestion.track), 0)
    }

    private fun resolveSuggestion(code: String, suggestion: Suggestion) {
        updateRoom(code) { r -> r.copy(suggestions = r.suggestions.filterNot { it.track.id == suggestion.track.id }) }
        send(code, TogetherMessage.SuggestionDone(memberId, System.currentTimeMillis(), suggestion.track.id))
    }

    private fun addToPlaylist(code: String, track: SharedTrack) {
        updateRoom(code) { r -> if (r.playlist.any { it.id == track.id }) r else r.copy(playlist = r.playlist + track) }
        publishQueue(code, force = true)
    }

    /** A pause, play, seek, skip from this listener: the host applies it, a member asks the host. */
    fun control(action: String, positionMs: Long) {
        val code = _active.value ?: return
        if (isHostOf(code)) publishStateSoon(code)
        else send(code, TogetherMessage.Control(memberId, System.currentTimeMillis(), action, positionMs))
    }

    /** Whether this listener may switch automix: only the host, while listening along. */
    fun mayChangeAutomix(): Boolean = _active.value?.let(::isHostOf) ?: true

    // ─── The host's broadcasts ────────────────────────────────────────────────────────────────

    /** Called by the player whenever what plays changes, so the others follow at once rather than at the next tick. */
    fun onHostPlaybackChanged() {
        val code = _active.value ?: return
        if (!isHostOf(code)) return
        scope.launch {
            publishQueue(code, force = false)
            publishStateSoon(code)
        }
    }

    /**
     * Publishes the state once the player has acted on what just happened. A pause or a seek calls in before the player
     * has done it, so a state sent at once still said "playing" at the old spot and the others kept playing until the
     * next tick, four seconds later.
     */
    private fun publishStateSoon(code: String) {
        scope.launch {
            delay(SETTLE_BEFORE_STATE_MS)
            publishState(code)
        }
    }

    private fun publishState(code: String) {
        val p = player ?: return
        val now = System.currentTimeMillis()
        val current = p.sharedCurrent()
        val version = synchronized(queueStateLock) { queueVersion }
        updateRoom(code) { it.copy(hostId = memberId, hostSeenAt = now, nowPlaying = current, isPlaying = p.sharedIsPlaying(), automix = p.sharedAutomix()) }
        send(code, TogetherMessage.State(memberId, now, current, p.sharedPositionMs(), p.sharedIsPlaying(), p.sharedAutomix(), version))
    }

    private fun publishQueue(code: String, force: Boolean) {
        val p = player ?: return
        val upcoming = p.sharedUpcoming().take(MAX_SHARED_UPCOMING)
        val ids = upcoming.map { it.id }
        val version = synchronized(queueStateLock) {
            if (!force && ids == lastUpcomingIds && lastSentQueueVersion == queueVersion) return
            if (ids != lastUpcomingIds) queueVersion++
            lastUpcomingIds = ids
            lastSentQueueVersion = queueVersion
            queueVersion
        }
        val room = _rooms.value[code] ?: Room(code, "")
        updateRoom(code) { it.copy(upcoming = upcoming, queueVersion = version) }
        send(code, TogetherMessage.Queue(memberId, System.currentTimeMillis(), version, upcoming, room.playlist, room.name))
    }

    // ─── Incoming ─────────────────────────────────────────────────────────────────────────────

    internal suspend fun handle(code: String, message: TogetherMessage) {
        if (message.from == memberId) return
        val now = System.currentTimeMillis()
        when (message) {
            is TogetherMessage.Hello -> updateRoom(code) { r ->
                r.copy(members = r.members + (message.from to Member(message.from, message.name, message.isListening, now)))
            }
            is TogetherMessage.Bye -> updateRoom(code) { r ->
                val leaving = r.members[message.from]
                r.copy(
                    members = if (leaving != null) r.members + (message.from to leaving.copy(isListening = false)) else r.members,
                    hostId = if (r.hostId == message.from) null else r.hostId,
                )
            }
            is TogetherMessage.ClaimHost -> updateRoom(code) { r ->
                // The latest claim wins; two at the same millisecond go to the larger id. Everyone applies the same
                // rule to the same claims, so everyone agrees on the host.
                val newer = message.sentAt > r.hostClaimedAt ||
                    (message.sentAt == r.hostClaimedAt && message.from > (r.hostId ?: ""))
                if (newer) r.copy(hostId = message.from, hostSeenAt = now, hostClaimedAt = message.sentAt) else r
            }
            is TogetherMessage.State -> {
                updateRoom(code) { it.copy(hostId = message.from, hostSeenAt = now, nowPlaying = message.track, isPlaying = message.isPlaying, automix = message.automix) }
                if (_active.value == code && !isHostOf(code)) {
                    val room = _rooms.value[code]
                    if (room != null && room.queueVersion != message.queueVersion) send(code, TogetherMessage.SyncRequest(memberId, now))
                    val track = message.track ?: return
                    val drift = if (message.isPlaying) flightTime(message.from, now - message.sentAt) else 0L
                    // Never past the end of the song, whatever the host's report says.
                    val target = (message.positionMs + drift).coerceAtLeast(0L).let { p -> track.durationMs?.takeIf { it > 0 }?.let { p.coerceAtMost(it) } ?: p }
                    withContext(Dispatchers.Main) {
                        player?.followHost(track, target, message.isPlaying, room?.upcoming, message.automix)
                    }
                }
            }
            is TogetherMessage.Queue -> updateRoom(code) { r ->
                r.copy(
                    upcoming = message.upcoming,
                    playlist = if (message.playlist.isNotEmpty() || r.playlist.isEmpty()) message.playlist else r.playlist,
                    name = message.name.ifBlank { r.name },
                    queueVersion = message.version,
                )
            }.also { renameSaved(code, message.name) }
            is TogetherMessage.SyncRequest -> {
                val room = _rooms.value[code]
                if (isHostOf(code)) {
                    publishQueue(code, force = true)
                    publishState(code)
                } else if (room != null && room.hostId == null && room.playlist.isNotEmpty() && _saved.value.any { it.code == code }) {
                    // Nobody hosts: whoever has the playlist shares it, so a newcomer sees the tracks at once.
                    send(code, TogetherMessage.Queue(memberId, now, room.queueVersion, emptyList(), room.playlist, room.name))
                }
            }
            is TogetherMessage.PlayNext -> if (isHostOf(code)) withContext(Dispatchers.Main) { player?.hostPlayNext(message.track) }
            is TogetherMessage.Suggest -> updateRoom(code) { r ->
                if (r.suggestions.any { it.track.id == message.track.id }) r
                else r.copy(suggestions = r.suggestions + Suggestion(message.track, message.byName, message.from))
            }
            is TogetherMessage.SuggestionDone -> updateRoom(code) { r -> r.copy(suggestions = r.suggestions.filterNot { it.track.id == message.trackId }) }
            is TogetherMessage.Control -> if (isHostOf(code)) withContext(Dispatchers.Main) {
                player?.hostControl(message.action, message.positionMs)
            }
        }
    }

    /** Per host, the smallest "arrived minus sent" seen: the two clocks' difference plus the quickest trip. */
    private val quickestTrip = HashMap<String, Long>()

    /**
     * How long a message was on its way, given the gap between the host's clock at sending and this one on arrival.
     *
     * The two computers' clocks are not the same, and that gap used to be taken for travel time: a clock ten seconds ahead
     * of the host's made every follower play ten seconds ahead of them. Only the excess over the quickest message seen
     * is real delay, since the quickest is as close to zero travel as the connection ever gets.
     */
    internal fun flightTime(host: String, arrivedMinusSent: Long): Long {
        val floor = synchronized(quickestTrip) {
            val known = quickestTrip[host]
            if (known == null || arrivedMinusSent < known) { quickestTrip[host] = arrivedMinusSent; arrivedMinusSent } else known
        }
        return (arrivedMinusSent - floor + TYPICAL_TRIP_MS).coerceIn(0L, MAX_FLIGHT_MS)
    }

    private fun renameSaved(code: String, name: String) {
        if (name.isBlank()) return
        val list = _saved.value
        if (list.none { it.code == code && it.name != name }) return
        _saved.value = list.map { if (it.code == code) it.copy(name = name) else it }
        persistSaved()
    }

    private fun send(code: String, message: TogetherMessage) = relay.send(code, message)

    private fun updateRoom(code: String, change: (Room) -> Room) {
        synchronized(this) {
            val saved = _saved.value.firstOrNull { it.code == code }
            val current = _rooms.value[code] ?: Room(code, saved?.name.orEmpty(), playlist = saved?.tracks.orEmpty())
            val updated = change(current)
            _rooms.value = _rooms.value + (code to updated)
            if (saved != null && updated.playlist != saved.tracks) {
                _saved.value = _saved.value.map { if (it.code == code) it.copy(tracks = updated.playlist) else it }
                persistSaved()
            }
        }
    }

    private fun persistSaved() {
        prefs()?.edit()?.putString(KEY_SAVED, gson.toJson(_saved.value))?.apply()
    }

    private fun loadSaved(): List<Saved> = runCatching {
        prefs()?.getString(KEY_SAVED, null)?.let { gson.fromJson<List<Saved>>(it, object : TypeToken<List<Saved>>() {}.type) }
    }.getOrNull().orEmpty()
}
