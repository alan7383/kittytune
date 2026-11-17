package com.alananasss.kittytune.data.together

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What travels between the listeners of a shared playlist, and how (issue #66, ported from desktop).
 *
 * The relay is a public MQTT broker, so nothing is sent in the clear: every message is sealed with AES-GCM under a
 * key derived from the playlist's code, and the topic is a hash of the code too. The broker, and anyone else on it,
 * sees only noise on a meaningless topic; whoever has the code reads and writes the playlist.
 *
 * Wire compatibility: the topic derivation, key derivation, cipher parameters and the JSON shape are byte-for-byte
 * identical to KittyTune Desktop, so an Android listener and a desktop listener share the same playlist.
 */
internal object TogetherWire {

    private val gson = Gson()
    private val random = SecureRandom()

    // Topic and key derivations are pure functions of the code, and a message is sent every few
    // seconds per playlist: both are cached per code, bounded by how many playlists are followed.
    private val topicCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val keyCache = java.util.concurrent.ConcurrentHashMap<String, SecretKeySpec>()

    fun topicFor(code: String): String = topicCache.computeIfAbsent(normalizeCode(code)) {
        "kittytune/together/" + sha256Hex("topic:" + it).take(32)
    }

    fun seal(code: String, message: TogetherMessage): ByteArray {
        val json = gson.toJson(message.toJson()).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(code), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(json)
    }

    /** The message in [payload], or null for anything not sealed with this code (another playlist, noise). */
    fun open(code: String, payload: ByteArray): TogetherMessage? = runCatching {
        if (payload.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyFor(code), GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_BYTES)))
        val json = String(cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size)), Charsets.UTF_8)
        TogetherMessage.fromJson(gson.fromJson(json, JsonObject::class.java))
    }.getOrNull()

    /** Codes are typed by people: case, dashes and spaces do not matter. */
    fun normalizeCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    /** A new code: eight letters and digits, without the ones that read alike (0/O, 1/I/L). */
    fun newCode(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..CODE_LENGTH).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    /** "ABCDEFGH" as "ABCD-EFGH", for reading out. */
    fun displayCode(code: String): String = normalizeCode(code).chunked(4).joinToString("-")

    private fun keyFor(code: String): SecretKeySpec = keyCache.computeIfAbsent(normalizeCode(code)) {
        SecretKeySpec(sha256("key:kittytune-together:" + it), "AES")
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(text: String) = sha256(text).joinToString("") { "%02x".format(it) }

    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    const val CODE_LENGTH = 8
}

/** A track as the shared playlist passes it around: enough to show it and for the player to fetch the rest. */
data class SharedTrack(
    val id: Long,
    val title: String?,
    val artist: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val permalinkUrl: String?,
    val source: String?,
    val userId: Long,
    val username: String?,
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        user = User(userId, username, null),
        source = source,
        permalinkUrl = permalinkUrl,
    )

    companion object {
        fun of(track: Track) = SharedTrack(
            id = track.id,
            title = track.title,
            artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() },
            artworkUrl = track.fullResArtwork,
            durationMs = track.durationMs,
            permalinkUrl = track.permalinkUrl,
            source = track.source,
            userId = track.user?.id ?: 0L,
            username = track.user?.username,
        )
    }
}

/** One message between the members of a shared playlist. Every one says who sent it and when. */
sealed interface TogetherMessage {
    val from: String
    val sentAt: Long

    /** "I am here", every few seconds, with whether this member is listening along. */
    data class Hello(override val from: String, override val sentAt: Long, val name: String, val isListening: Boolean) : TogetherMessage

    /** Leaving, so the others need not wait for the presence to time out. */
    data class Bye(override val from: String, override val sentAt: Long) : TogetherMessage

    /** The host's playback: what plays, where, and whether; [queueVersion] says whether to ask for the queue. */
    data class State(
        override val from: String,
        override val sentAt: Long,
        val track: SharedTrack?,
        val positionMs: Long,
        val isPlaying: Boolean,
        val automix: Boolean,
        val queueVersion: Int,
    ) : TogetherMessage

    /** The host's queue from the song playing on, and the playlist's own tracks. */
    data class Queue(override val from: String, override val sentAt: Long, val version: Int, val upcoming: List<SharedTrack>, val playlist: List<SharedTrack>, val name: String) : TogetherMessage

    /** A newcomer asking the host for everything. */
    data class SyncRequest(override val from: String, override val sentAt: Long) : TogetherMessage

    /** "Play this next": the host puts it after the song playing, after the ones asked for before it. */
    data class PlayNext(override val from: String, override val sentAt: Long, val track: SharedTrack, val byName: String) : TogetherMessage

    /** "Add this to the playlist": for the host to accept, listen to, or turn down. */
    data class Suggest(override val from: String, override val sentAt: Long, val track: SharedTrack, val byName: String) : TogetherMessage

    /** The host's answer to a suggestion, so everyone's list of suggestions agrees. */
    data class SuggestionDone(override val from: String, override val sentAt: Long, val trackId: Long) : TogetherMessage

    /** Pause, play or seek, asked for by anyone; the host applies it and everyone follows the host. */
    data class Control(override val from: String, override val sentAt: Long, val action: String, val positionMs: Long) : TogetherMessage

    /** Taking over as host. The earliest claim wins, ties by member id. */
    data class ClaimHost(override val from: String, override val sentAt: Long) : TogetherMessage

    fun toJson(): JsonObject {
        // The companion's shared instance: a fresh Gson per message was pure allocation churn.
        val obj = gson.toJsonTree(this).asJsonObject
        obj.addProperty("type", this::class.simpleName)
        return obj
    }

    companion object {
        private val gson = Gson()
        private val types = mapOf(
            "Hello" to Hello::class.java,
            "Bye" to Bye::class.java,
            "State" to State::class.java,
            "Queue" to Queue::class.java,
            "SyncRequest" to SyncRequest::class.java,
            "PlayNext" to PlayNext::class.java,
            "Suggest" to Suggest::class.java,
            "SuggestionDone" to SuggestionDone::class.java,
            "Control" to Control::class.java,
            "ClaimHost" to ClaimHost::class.java,
        )

        fun fromJson(obj: JsonObject): TogetherMessage? {
            val type = types[obj.get("type")?.asString] ?: return null
            return gson.fromJson(obj, type)
        }

        const val CONTROL_PAUSE = "pause"
        const val CONTROL_PLAY = "play"
        const val CONTROL_SEEK = "seek"
        const val CONTROL_NEXT = "next"
        const val CONTROL_PREVIOUS = "previous"
    }
}
