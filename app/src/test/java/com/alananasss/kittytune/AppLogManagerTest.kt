package com.alananasss.kittytune

import com.alananasss.kittytune.utils.AppLogManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLogManagerTest {

    @Test
    fun testSanitizeLogLine() {
        val bearer = "Authorization: Bearer mySecretToken123456"
        assertEquals("Authorization: Bearer [REDACTED]", AppLogManager.sanitizeLogLine(bearer))

        val clientSecret = "client_secret=super_secret_key"
        assertEquals("client_secret=[REDACTED]", AppLogManager.sanitizeLogLine(clientSecret))

        val oauth = "oauth_token=token_abc_xyz"
        assertEquals("oauth_token=[REDACTED]", AppLogManager.sanitizeLogLine(oauth))

        val cleanLine = "Normal logcat message from ExoPlayer"
        assertEquals(cleanLine, AppLogManager.sanitizeLogLine(cleanLine))
    }
}
