package com.alananasss.kittytune

import com.alananasss.kittytune.data.zapret.FragmentSpec
import com.alananasss.kittytune.data.zapret.ZapretHostList
import com.alananasss.kittytune.data.zapret.ZapretServices
import com.alananasss.kittytune.data.zapret.ZapretStrategy
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
}
