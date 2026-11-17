package com.alananasss.kittytune

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class DiscordRemoteAuthTest {

    @Test
    fun testRsaOaepEncryptionAndDecryption() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()

        val sampleToken = "mfa.ABcdEFghIJklMNopQRstUVwxYZ_0123456789"

        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )

        // Encrypt with public key
        val encryptCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, keyPair.public, oaepSpec)
        val cipherText = encryptCipher.doFinal(sampleToken.toByteArray(Charsets.UTF_8))

        // Decrypt with private key
        val decryptCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, keyPair.private, oaepSpec)
        val plainText = decryptCipher.doFinal(cipherText)

        assertEquals(sampleToken, String(plainText, Charsets.UTF_8))
    }

    @Test
    fun testNonceHashingProof() {
        val nonce = "random_nonce_from_discord_gateway".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(nonce)
        assertNotNull(digest)
        assertTrue(digest.isNotEmpty())
    }
}
