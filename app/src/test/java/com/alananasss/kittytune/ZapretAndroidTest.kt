package com.alananasss.kittytune

import com.alananasss.kittytune.data.zapret.FragmentSpec
import com.alananasss.kittytune.data.zapret.ZapretHostList
import com.alananasss.kittytune.data.zapret.ZapretServices
import com.alananasss.kittytune.data.zapret.ZapretStrategy
import com.alananasss.kittytune.domain.isDefaultAvatar
import com.alananasss.kittytune.domain.getHighResAvatarUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZapretAndroidTest {

    @Test
    fun addingToAFileWithoutATrailingNewlineKeepsItsLastLine() {
        val text = "domain.example.abc\nrootapp.com\nrootappcdn.com"
        val result = ZapretHostList.withOwnDomains(text, listOf("sndcdn.com"))
        assertEquals(
            "domain.example.abc\nrootapp.com\nrootappcdn.com\n${ZapretHostList.BEGIN}\nsndcdn.com\n${ZapretHostList.END}\n",
            result,
        )
    }

    @Test
    fun removingOurBlockLeavesTheRestUntouched() {
        val text = "a.com\n${ZapretHostList.BEGIN}\nsndcdn.com\n${ZapretHostList.END}\nb.com\n"
        assertEquals("a.com\nb.com\n", ZapretHostList.withOwnDomains(text, emptyList()))
        assertEquals(listOf("sndcdn.com"), ZapretHostList.ownDomains(text))
    }

    @Test
    fun anEmptiedListKeepsAPlaceholderBecauseZapretRejectsEmptyOnes() {
        val text = "${ZapretHostList.BEGIN}\nsndcdn.com\n${ZapretHostList.END}\n"
        assertEquals("domain.example.abc\n", ZapretHostList.withOwnDomains(text, emptyList()))
    }

    @Test
    fun aParentDomainCoversItsSubdomains() {
        val covered = setOf("youtube.com")
        assertTrue(ZapretHostList.isCovered("music.youtube.com", covered))
        assertFalse(ZapretHostList.isCovered("youtube.co", covered))
    }

    @Test
    fun coverageIsCaseInsensitiveAndIgnoresTrailingDots() {
        val covered = setOf("googlevideo.com")
        assertTrue(ZapretHostList.isCovered("R1---SN-EXAMPLE.googlevideo.com.", covered))
        assertTrue(ZapretHostList.isCovered("GOOGLEVIDEO.COM", covered))
        assertFalse(ZapretHostList.isCovered("googlevideo.com.evil.com", covered))
    }

    @Test
    fun domainsIgnoreCommentsAndBlankLines() {
        val text = "# comment\n\n  SoundCloud.COM  # inline\nsndcdn.com\n"
        assertEquals(listOf("soundcloud.com", "sndcdn.com"), ZapretHostList.domains(text))
    }

    @Test
    fun exportProducesAValidHostList() {
        val exported = ZapretHostList.withOwnDomains("", listOf("youtube.com", "googlevideo.com"))
        assertTrue(exported.contains(ZapretHostList.BEGIN))
        assertTrue(exported.contains("googlevideo.com"))
        assertEquals(listOf("youtube.com", "googlevideo.com"), ZapretHostList.ownDomains(exported))
    }

    @Test
    fun everyServiceHasProbesAndDomains() {
        assertTrue(ZapretServices.ALL.isNotEmpty())
        ZapretServices.ALL.forEach { service ->
            assertTrue("${service.id} needs probe URLs", service.probeUrls.isNotEmpty())
            assertTrue("${service.id} needs domains", service.domains.isNotEmpty())
            service.probeUrls.forEach { assertTrue(it.startsWith("https://")) }
        }
    }

    @Test
    fun androidOnlySourcesAreCovered() {
        val ids = ZapretServices.ALL.map { it.id }.toSet()
        // VK login + music, Shazam recognition, KuGou lyrics, SongLink matching and the
        // lyrics translator are Android-app traffic the desktop catalogue never needed.
        assertTrue(ids.containsAll(setOf("vk", "shazam", "kugou", "songlink", "translator")))
        val domains = ZapretServices.ALL.flatMap { it.domains }.toSet()
        assertTrue(domains.containsAll(setOf("vk.com", "vk.ru", "shazam.com", "kugou.com", "song.link")))
        // Lyrics mirrors, the YouTube playback fallback and the artwork placeholder host.
        assertTrue(domains.containsAll(setOf("binimum.org", "prjktla.my.id", "atomix.one", "kavin.rocks", "picsum.photos")))
    }

    @Test
    fun everyProbeHostIsBypassCoveredByItsOwnService() {
        // The "tout sondé" invariant: a blocked probe must always resolve to a bypassed
        // domain, otherwise the check could report Blocked with nothing to add.
        ZapretServices.ALL.forEach { service ->
            val covered = service.domains.toSet()
            service.probeUrls.forEach { url ->
                val host = url.removePrefix("https://").substringBefore('/').substringBefore('?')
                assertTrue("${service.id}: probe host $host should be bypass-covered", ZapretHostList.isCovered(host, covered))
            }
        }
    }

    @Test
    fun soundcloudProbesEveryEdgeTheAppUses() {
        val soundcloud = ZapretServices.ALL.first { it.id == "soundcloud" }
        val probes = soundcloud.probeUrls.joinToString(" ")
        // api-mobile (account actions), graph (graphql import) and api-auth (session refresh)
        // are separate edges: the front page can load while one of them is blocked.
        assertTrue(probes.contains("api-mobile.soundcloud.com"))
        assertTrue(probes.contains("graph.soundcloud.com"))
        assertTrue(probes.contains("api-auth.soundcloud.com"))
    }

    @Test
    fun strategyFallsBackToSplit2() {
        assertEquals(ZapretStrategy.SPLIT2, ZapretStrategy.fromId(null))
        assertEquals(ZapretStrategy.SPLIT2, ZapretStrategy.fromId("nope"))
        assertEquals(ZapretStrategy.MULTI, ZapretStrategy.fromId("multi"))
    }

    @Test
    fun strategiesFragmentTheFirstFlightInSmallChunks() {
        val split2 = FragmentSpec.forStrategy(ZapretStrategy.SPLIT2)
        val multi = FragmentSpec.forStrategy(ZapretStrategy.MULTI)
        // A typical ClientHello (~300-600 B) must not fit in a single chunk…
        assertTrue(split2.chunkBytes < 1500)
        assertTrue(multi.chunkBytes < split2.chunkBytes)
        // …and only the handshake budget is fragmented, the stream itself flows untouched.
        assertTrue(split2.budgetBytes in 1_024..16_384)
        assertTrue(multi.budgetBytes >= split2.budgetBytes)
        assertTrue(split2.delayMs > 0)
    }

    @Test
    fun coversAndCdnsAreCoveredByServices() {
        val yt = ZapretServices.ALL.first { it.id == "youtube" }
        assertTrue("YouTube should cover googleusercontent for covers", ZapretHostList.isCovered("lh3.googleusercontent.com", yt.domains.toSet()))
        assertTrue("YouTube should cover ytimg", ZapretHostList.isCovered("i.ytimg.com", yt.domains.toSet()))

        val sc = ZapretServices.ALL.first { it.id == "soundcloud" }
        assertTrue("SoundCloud should cover sndcdn for artworks", ZapretHostList.isCovered("i1.sndcdn.com", sc.domains.toSet()))
        assertTrue("SoundCloud should cover sndcdn.net", ZapretHostList.isCovered("cf-media.sndcdn.net", sc.domains.toSet()))

        assertTrue("ALL_DOMAINS should not be empty", ZapretServices.ALL_DOMAINS.isNotEmpty())
        assertTrue(ZapretServices.ALL_DOMAINS.contains("googleusercontent.com"))
        assertTrue(ZapretServices.ALL_DOMAINS.contains("sndcdn.com"))
        assertTrue(ZapretServices.ALL_DOMAINS.contains("gstatic.com"))
        assertTrue(ZapretServices.ALL_DOMAINS.contains("userapi.com"))
        assertTrue(ZapretServices.ALL_DOMAINS.contains("picsum.photos"))

        val vk = ZapretServices.ALL.first { it.id == "vk" }
        assertTrue("VK should cover userapi.com for avatar/cover images", ZapretHostList.isCovered("sun9-1.userapi.com", vk.domains.toSet()))

        val helpers = ZapretServices.ALL.first { it.id == "helpers" }
        assertTrue("Helpers should cover picsum.photos for placeholder covers", ZapretHostList.isCovered("picsum.photos", helpers.domains.toSet()))
    }

    @Test
    fun isCoveredFallsBackToAllDomainsWhenStateDomainsEmpty() {
        // Even if user's saved domain preference is empty or stale, all service domains must be covered
        assertTrue(com.alananasss.kittytune.data.zapret.ZapretManager.isCovered("i1.sndcdn.com"))
        assertTrue(com.alananasss.kittytune.data.zapret.ZapretManager.isCovered("lh3.googleusercontent.com"))
        assertTrue(com.alananasss.kittytune.data.zapret.ZapretManager.isCovered("sun9-1.userapi.com"))
    }

    @Test
    fun defaultPortDoesNotCollideWithPopularVpnClients() {
        // v2rayNG, NekoBox, and Shadowsocks default to 10808; KittyTune must not collide with it
        assertTrue(com.alananasss.kittytune.data.zapret.ZapretManager.DEFAULT_PORT != 10808)
        assertEquals(10898, com.alananasss.kittytune.data.zapret.ZapretManager.DEFAULT_PORT)
    }

    @Test
    fun zapretStateTracksVpnPause() {
        val state = com.alananasss.kittytune.data.zapret.ZapretState(
            enabled = true,
            isPausedForVpn = true
        )
        assertTrue(state.enabled)
        assertTrue(state.isPausedForVpn)
    }

    @Test
    fun multiServiceDomainsAreCovered() {
        val spotify = ZapretServices.ALL.first { it.id == "spotify" }
        assertTrue(ZapretHostList.isCovered("audio-ak-spotify-com.akamaized.net", spotify.domains.toSet()))
        assertTrue(ZapretHostList.isCovered("i.scdn.co", spotify.domains.toSet()))

        val tidal = ZapretServices.ALL.first { it.id == "tidal" }
        assertTrue(ZapretHostList.isCovered("api.tidal.com", tidal.domains.toSet()))
        assertTrue(ZapretHostList.isCovered("song.link", tidal.domains.toSet()))

        val qobuz = ZapretServices.ALL.first { it.id == "qobuz" }
        assertTrue(ZapretHostList.isCovered("play.qobuz.com", qobuz.domains.toSet()))
        assertTrue(ZapretHostList.isCovered("static.qobuz.com", qobuz.domains.toSet()))

        val yt = ZapretServices.ALL.first { it.id == "youtube" }
        assertTrue(ZapretHostList.isCovered("youtu.be", yt.domains.toSet()))
    }

    @Test
    fun soundCloudArtworkAndDefaultAvatarsAreProperlyFormatted() {
        val defaultAvatar = "https://a1.sndcdn.com/images/default_avatar_large.png"
        val customAvatar = "https://i1.sndcdn.com/avatars-000123-abcdef-large.jpg"

        assertTrue(defaultAvatar.isDefaultAvatar())
        assertFalse(customAvatar.isDefaultAvatar())

        // Default avatar must not be changed to t500x500 (which 404s on SoundCloud)
        assertEquals(defaultAvatar, defaultAvatar.getHighResAvatarUrl())

        // Custom avatar should be upgraded to t500x500
        assertEquals(
            "https://i1.sndcdn.com/avatars-000123-abcdef-t500x500.jpg",
            customAvatar.getHighResAvatarUrl()
        )
    }
}
