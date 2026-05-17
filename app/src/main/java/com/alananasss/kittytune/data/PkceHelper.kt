package com.alananasss.kittytune.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PKCE (Proof Key for Code Exchange) helper that mirrors the implementation
 * found in SoundCloud's official Android app (v2025.12.10).
 *
 * Flow:
 * 1. [generateVerifier] creates a 64-char random code_verifier.
 * 2. [generateChallenge] SHA-256 hashes the verifier and Base64-URL-encodes the digest.
 * 3. The challenge goes into the auth URL; the verifier is sent in the token exchange.
 */
object PkceHelper {

    private const val VERIFIER_LENGTH = 64
    private const val VALID_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val VALID_CHAR_COUNT = 66 // 0x42 in SoundCloud's smali

    /**
     * Generates a PKCE code_verifier identical to the official SoundCloud app.
     * Uses SecureRandom for cryptographic safety.
     */
    fun generateVerifier(): String {
        val random = SecureRandom()
        val chars = CharArray(VERIFIER_LENGTH)
        for (i in 0 until VERIFIER_LENGTH) {
            val idx = (random.nextInt(1000)) % VALID_CHAR_COUNT
            chars[i] = VALID_CHARS[idx]
        }
        return String(chars)
    }

    /**
     * Generates a PKCE code_challenge from the verifier using SHA-256 + Base64URL.
     * This matches SoundCloud's S256 code_challenge_method.
     */
    fun generateChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
