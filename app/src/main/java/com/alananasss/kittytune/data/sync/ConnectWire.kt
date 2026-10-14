package com.alananasss.kittytune.data.sync

import com.google.gson.*
import com.alananasss.kittytune.domain.Track
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Identical on Android/desktop. The relay receives only random room IDs and encrypted frames. */
data class ConnectCredentials(val room: String, val token: String, val key: ByteArray) {
    companion object {
        fun derive(id: String, secret: String, peerId: String, peerSecret: String): ConnectCredentials {
            require(id.isNotBlank() && peerId.isNotBlank() && id != peerId)
            require(secret.length >= 20 && peerSecret.length >= 20)
            val input = listOf(id to secret, peerId to peerSecret).sortedBy { it.first }
                .joinToString("\u0000") { "${it.first}\u0000${it.second}" }
            fun hash(label: String) = MessageDigest.getInstance("SHA-256")
                .digest("kitty-connect/v1/$label\u0000$input".toByteArray(Charsets.UTF_8))
            fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
            return ConnectCredentials(hex(hash("room")), hex(hash("auth")), hash("encryption"))
        }
    }
}

data class ConnectMessage(
    val version: Int = 1,
    val kind: String,
    val sender: String,
    val session: String,
    val sequence: Long,
    val challenge: String = "",
    val replyTo: String = "",
    val id: String = "",
    val action: String = "",
    val value: Long = 0,
    val value2: Long = 0,
    val trackId: Long? = null,
    val queueVersion: String = "",
    val state: PlaybackSnapshot? = null,
    val ok: Boolean = false,
    val error: String = "",
    val compression: String = "",
)

object ConnectWire {
    const val MAX_BYTES = 2 * 1024 * 1024
    // Queue entries are references to playable tracks, not copies of API responses.
    private val gson = GsonBuilder().registerTypeAdapter(Track::class.java, JsonSerializer<Track> { track, _, context ->
        JsonObject().apply {
            addProperty("id", track.id); addProperty("title", track.title)
            addProperty("artwork_url", track.artworkUrl); addProperty("duration", track.durationMs)
            addProperty("source", track.source); addProperty("permalink_url", track.permalinkUrl)
            addProperty("permalink", track.permalink); addProperty("secret_token", track.secretToken)
            addProperty("user_favorite", track.isLiked); addProperty("policy", track.policy)
            addProperty("monetization_model", track.monetizationModel)
            add("publisher_metadata", context.serialize(track.publisherMetadata))
            add("artists", context.serialize(track.artists))
            track.user?.let { user -> add("user", JsonObject().apply {
                addProperty("id", user.id); addProperty("username", user.username)
                addProperty("avatar_url", user.avatarUrl); addProperty("urn", user.urn)
                addProperty("permalink_url", user.permalinkUrl)
            }) }
        }
    }).create()
    private val random = SecureRandom()
    private val gzipHeader = byteArrayOf(75, 84, 67, 50)
    fun seal(credentials: ConnectCredentials, message: ConnectMessage, compress: Boolean = false): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(credentials.key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(credentials.room.toByteArray(Charsets.UTF_8))
        var bytes = gson.toJson(message).toByteArray(Charsets.UTF_8)
        require(bytes.size < MAX_BYTES * 3 / 4 - 32)
        if (compress && bytes.size > 1024) {
            val output = ByteArrayOutputStream()
            GZIPOutputStream(output).use { it.write(bytes) }
            val packed = gzipHeader + output.toByteArray()
            if (packed.size < bytes.size) bytes = packed
        }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(bytes))
    }

    fun open(credentials: ConnectCredentials, text: String): ConnectMessage? = runCatching {
        require(text.length <= MAX_BYTES)
        val bytes = Base64.getDecoder().decode(text)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(credentials.key, "AES"),
            GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(credentials.room.toByteArray(Charsets.UTF_8))
        var decoded = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        if (decoded.size >= 4 && decoded.copyOfRange(0, 4).contentEquals(gzipHeader)) {
            decoded = GZIPInputStream(ByteArrayInputStream(decoded, 4, decoded.size - 4)).use {
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = it.read(chunk)
                    if (count < 0) break
                    require(output.size() + count < MAX_BYTES * 3 / 4 - 32)
                    output.write(chunk, 0, count)
                }
                output.toByteArray()
            }
        }
        require(decoded.size < MAX_BYTES * 3 / 4 - 32)
        gson.fromJson(String(decoded, Charsets.UTF_8),
            ConnectMessage::class.java).also {
            require(it.version == 1 && it.sequence > 0 && it.sender.isNotBlank() && it.session.length in 16..64)
        }
    }.getOrNull()

    fun queueVersion(state: PlaybackSnapshot): String = MessageDigest.getInstance("SHA-256")
        .digest(state.queue.joinToString("|") { "${it.source}:${it.id}" }.toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }
}

/** Reject replay and reordering within a connection; old sessions cannot reappear on that connection. */
class ConnectReplayGuard {
    private var session: String? = null
    private var sequence = 0L
    private val retired = LinkedHashSet<String>()
    @Synchronized fun accept(message: ConnectMessage): Boolean {
        if (session != null && session != message.session) {
            if (message.kind != "hello" || message.session in retired) return false
            retired.add(session!!)
            if (retired.size > 128) return false
            sequence = 0L
        }
        if (message.sequence <= sequence) return false
        session = message.session
        sequence = message.sequence
        return true
    }
}

object ConnectPolicy {
    fun shouldConnect(enabled: Boolean, playbackSync: Boolean, mobile: Boolean, foreground: Boolean,
        playing: Boolean): Boolean = enabled && playbackSync && (!mobile || foreground || playing)
}

/** Explicit device choices stay selected; incoming renderer commands retain automatic following. */
class ConnectDeviceSelection {
    var selectedDevice: String? = null
        private set
    private var autoFollow = true
    fun choose(id: String?) { autoFollow = false; selectedDevice = id }
    fun activateLocalRenderer() { autoFollow = true; selectedDevice = null }
    fun followActivePeer(id: String?, localPlaying: Boolean) {
        if (autoFollow && selectedDevice == null && !localPlaying) selectedDevice = id
    }
}
