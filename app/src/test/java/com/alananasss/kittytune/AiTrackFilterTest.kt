package com.alananasss.kittytune

import com.alananasss.kittytune.data.filter.AiScoringResult
import com.alananasss.kittytune.data.filter.AiWaveformSignals
import com.alananasss.kittytune.data.filter.SoundCloudAiScorer
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.BasicTrackCollection
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.domain.CommentCollection
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.domain.normalizeTrackTitle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class AiTrackFilterTest {

    private val now = Instant.parse("2026-09-01T12:00:00Z").toEpochMilli()

    private fun ago(days: Long, hours: Long = 0): String =
        Instant.ofEpochMilli(now - days * DAY_MS + hours * HOUR_MS).toString()

    private fun createSampleTrack(
        id: Long = 1001L,
        title: String = "Test Track",
        artworkUrl: String? = null,
        description: String = "",
        tagList: String = "",
        genre: String? = null,
        createdAt: String? = null,
        playbackCount: Int = 1000,
        likesCount: Int = 50,
        repostsCount: Int = 10,
        userCreatedAt: String? = null,
        userTrackCount: Int = 10,
        verified: Boolean = false
    ): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = artworkUrl,
            durationMs = 180_000L,
            user = User(
                id = UPLOADER_ID,
                username = "TestArtist",
                avatarUrl = null,
                createdAt = userCreatedAt,
                trackCount = userTrackCount,
                verified = verified
            ),
            description = description,
            tagList = tagList,
            genre = genre,
            createdAt = createdAt,
            playbackCount = playbackCount,
            likesCount = likesCount,
            repostsCount = repostsCount
        )
    }

    private fun score(
        track: Track,
        waveform: FloatArray? = null,
        profile: User? = null,
        catalog: List<Track>? = null,
        comments: List<Comment>? = null
    ): AiScoringResult = runBlocking {
        SoundCloudAiScorer.scoreTrack(
            track,
            waveformSamples = waveform,
            uploaderProfile = profile,
            uploaderTracks = catalog,
            comments = comments,
            nowMs = now
        )
    }

    private fun comment(userId: Long, body: String) =
        Comment(
            id = userId * 10,
            body = body,
            createdAt = ago(1),
            trackTimestamp = null,
            user = User(userId, "listener$userId", null)
        )

    private fun assertDeclared(result: AiScoringResult, reason: String = "METADATA") {
        assertTrue(result.isAi)
        assertEquals(100, result.score)
        assertEquals(reason, result.reason)
    }

    // --- Declarations: settle it on their own ---------------------------------------------------------------

    @Test
    fun `metadata with Suno keyword instantly flagged as AI`() {
        assertDeclared(score(createSampleTrack(title = "Dreamy Night (Suno v3)")))
    }

    @Test
    fun `metadata with Udio keyword instantly flagged as AI`() {
        assertDeclared(score(createSampleTrack(description = "Created with Udio AI music generation")))
    }

    @Test
    fun `metadata with AI Music keyword instantly flagged`() {
        assertDeclared(score(createSampleTrack(tagList = "ai music, electronic, synth")))
    }

    @Test
    fun `Verknallt in einen Talahon by Butterbro is detected as AI`() {
        val track = createSampleTrack(title = "Verknallt in einen Talahon")
            .copy(user = User(1420867032L, "Butterbroofficial", null))
        assertDeclared(score(track), reason = "KNOWN_AI_ARTIST")
    }

    @Test
    fun `documented AI act is detected by artist name`() {
        val track = createSampleTrack(title = "Dust on the Wind")
            .copy(publisherMetadata = TrackPublisherMetadata(artist = "The Velvet Sundown"))
        assertDeclared(score(track), reason = "KNOWN_AI_ARTIST")
    }

    @Test
    fun `German ki-generiert in title is detected as AI`() {
        assertDeclared(score(createSampleTrack(title = "Mit Den Jungs (ki-generiert)")))
        assertDeclared(score(createSampleTrack(description = "Die Musik wurde mit KI erstellt.")))
    }

    @Test
    fun `NotebookLM generated track is detected as AI`() {
        assertDeclared(score(createSampleTrack(title = "KI-Einführung 1 teilweise mit notebooklm generiert")))
    }

    @Test
    fun `underscore separated AI creator name like Suno_Al_Music is detected`() {
        val track = createSampleTrack(title = "Death of a Mournful Man").copy(user = User(1002L, "Suno_Al_Music", null))
        assertDeclared(score(track))
    }

    @Test
    fun `AI slop tag or title is detected`() {
        assertDeclared(score(createSampleTrack(title = "Some track (AI-Slop)")))
    }

    @Test
    fun `disguised AI labels are still detected`() {
        assertDeclared(score(createSampleTrack(title = "Midnight Rain (Ѕuno v4.5)"))) // Cyrillic S
        assertDeclared(score(createSampleTrack(description = "Fully A​I generated track"))) // zero-width space
        assertDeclared(score(createSampleTrack(title = "Neon Nights (ＡＩ Ｃｏｖｅｒ)"))) // fullwidth letters
        assertDeclared(score(createSampleTrack(description = "An A.I.-generated song")))
        assertDeclared(score(createSampleTrack(description = "Full song: https://suno.com/song/3f2a9c1e")))
    }

    @Test
    fun `AI labels in other languages are detected`() {
        assertDeclared(score(createSampleTrack(description = "Chanson générée par IA")))
        assertDeclared(score(createSampleTrack(description = "Canción hecha con IA")))
        assertDeclared(score(createSampleTrack(description = "Música feita com IA")))
        assertDeclared(score(createSampleTrack(description = "Brano generato con l'IA")))
        assertDeclared(score(createSampleTrack(description = "Песня сгенерирована нейросетью")))
        assertDeclared(score(createSampleTrack(description = "Пісня створена ШІ")))
        assertDeclared(score(createSampleTrack(title = "夜の街 (AI生成)")))
    }

    @Test
    fun `credits naming a generator are declarations`() {
        assertDeclared(score(createSampleTrack(description = "Musik: Suno, Text: ich")))
        assertDeclared(score(createSampleTrack(description = "Beat by AI.")))
    }

    // --- Guards against false alarms ------------------------------------------------------------------------

    @Test
    fun `human artist KI slash KI is not falsely detected as AI`() {
        val track = createSampleTrack(title = "What's a Girl to Do in '25", description = "Trance live set")
            .copy(user = User(2003L, "KI/KI", null))
        val result = score(track)
        assertFalse(result.isAi)
        assertEquals(0, result.score)
    }

    @Test
    fun `remixes by KI slash KI are not read as KI-Remix`() {
        listOf("Rave Anthem (KI/KI Remix)", "Rave Anthem (KI/KI-Remix)").forEach { title ->
            val track = createSampleTrack(title = title).copy(
                user = User(2003L, "KI/KI", null),
                permalinkUrl = "https://soundcloud.com/ki-ki/rave-anthem-ki-ki-remix"
            )
            assertEquals(0, score(track).score)
        }
    }

    @Test
    fun `a person called Ai is not artificial intelligence`() {
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(createSampleTrack(description = "Produced by Ai Otsuka")))
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(createSampleTrack(description = "Music by Ai Higuchi")))
    }

    @Test
    fun `AI mastering of a human recording is not AI music`() {
        val track = createSampleTrack(description = "AI-assisted mastering by LANDR, everything else by hand")
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(track))
        assertTrue(SoundCloudAiScorer.isExplicitlyLabeledAi(createSampleTrack(description = "Mixed AI vocals over my beat")))
    }

    @Test
    fun `Hindi suno meaning listen is not mistaken for the generator`() {
        val track = createSampleTrack(title = "Suno Na Sangemarmar").copy(user = User(2004L, "Arijit Singh", null))
        val result = score(track)
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(track))
        assertEquals(0, result.score)
    }

    @Test
    fun `negated AI mentions do not count`() {
        val track = createSampleTrack(description = "No AI generated parts. Kein KI-Song, alles handgemacht.")
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(track))
        assertEquals(0, score(track).score)
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(createSampleTrack(description = "原创音乐，非AI生成")))
    }

    @Test
    fun `a lyric sheet from a lyrics site stays a weak hint`() {
        val track = createSampleTrack(
            description = "[Verse 1]\nline\n[Chorus]\nline\n[Instrumental Break]\n[Bridge]\nline\n[Outro]"
        )
        val result = score(track)
        assertFalse(result.isAi)
        assertEquals("STRUCTURE_TAGS", result.reason)
    }

    @Test
    fun `AI cover art alone does not make the music AI`() {
        val track = createSampleTrack(description = "Cover art made with AI (Midjourney). Music and vocals by me.")
        val result = score(track)
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(track))
        assertFalse(result.isAi)
        assertEquals("AI_ARTWORK", result.reason)
    }

    @Test
    fun `VOCALOID singer IA is not read as AI`() {
        val track = createSampleTrack(title = "Senbonzakura (feat. IA)", tagList = "vocaloid")
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(track))
        assertFalse(score(track).isAi)
    }

    @Test
    fun `boomy as a sound description is not the generator`() {
        assertFalse(SoundCloudAiScorer.isExplicitlyLabeledAi(createSampleTrack(description = "Heavy, boomy 808s")))
    }

    @Test
    fun `a song about artificial intelligence is only a hint`() {
        val result = score(createSampleTrack(title = "Künstliche Intelligenz"))
        assertFalse(result.isAi)
        assertEquals("AI_TERM_MENTION", result.reason)
    }

    @Test
    fun `credits list is not a style prompt`() {
        val track = createSampleTrack(
            description = "Mixed by John Smith, Mastered by Mike Dean, Artwork by Anna Berg, Video by Tom Lee"
        )
        assertEquals(0, score(track).score)
    }

    // --- Heuristic evidence -----------------------------------------------------------------------------------

    @Test
    fun `pasted Suno prompt and meta tags are detected`() {
        val track = createSampleTrack(
            description = """
                Style of Music: dark synthwave, female vocals
                Weirdness: 60%

                [Intro]
                [Verse 1]
                Neon lights are calling me
                [Chorus]
                We rise tonight
                [Fade Out]
                [End]
            """.trimIndent()
        )
        val result = score(track)
        assertTrue(result.isAi)
        assertTrue(result.reason.contains("PROMPT_LEAK"))
        assertTrue(result.reason.contains("SUNO_METATAGS"))
    }

    @Test
    fun `a full style prompt with a voice description reaches the threshold on its own`() {
        val track = createSampleTrack(description = "outlaw country, gritty male vocals, steel guitar, melancholic, slow tempo")
        val result = score(track)
        assertTrue(result.isAi)
        assertEquals("PROMPT_TAGS", result.reason)
    }

    @Test
    fun `an uploader branded as AI adds up with other hints`() {
        val track = createSampleTrack(title = "Whispers of the Night").copy(user = User(3003L, "DreamscapeAI", null))
        val result = score(track)
        assertTrue(result.isAi)
        assertEquals("UPLOADER_NAME+LLM_TITLE", result.reason)
    }

    @Test
    fun `uploader bio declaring AI flags the track`() {
        val profile = User(UPLOADER_ID, "TestArtist", null, description = "AI artist. All songs created with Suno.")
        val result = score(createSampleTrack(), profile = profile)
        assertTrue(result.isAi)
        assertTrue(result.reason.contains("UPLOADER_BIO"))
    }

    @Test
    fun `other uploads labelled as AI give the account away`() {
        val labeled = listOf("Rain (Suno v4)", "Fire (Suno v4)", "Ice (suno ai)").mapIndexed { i, title ->
            createSampleTrack(id = 2000L + i, title = title)
        }
        val plain = (0 until 5).map { createSampleTrack(id = 3000L + it, title = "Untitled Idea ${'A' + it}") }
        val result = score(createSampleTrack(title = "Snow"), catalog = labeled + plain)
        assertTrue(result.isAi)
        assertTrue(result.reason.contains("CATALOG_AI_LABELED"))
    }

    @Test
    fun `AI farm behaviour is detected without any label`() {
        val profile = User(
            UPLOADER_ID, "SynthFactory", null,
            createdAt = ago(200), trackCount = 600, followersCount = 40
        )
        val titles = listOf(
            "Neon Dreams", "Neon Dreams (1)", "Lost Signal", "Lost Signal v2", "Golden Hour",
            "Golden Hour (2)", "Paper Moon", "Cold Water", "Wildfire", "Echo Park",
            "Blue Motel", "Highway Ghost", "Silver Lining", "Last Dance", "Open Road"
        )
        val genres = listOf("Country", "Metal", "Reggaeton", "K-Pop", "Lofi", "Trap", "Schlager", "Gospel", "Jazz")
        val catalog = titles.mapIndexed { i, title ->
            createSampleTrack(
                id = 4000L + i,
                title = title,
                genre = genres[i % genres.size],
                createdAt = ago(days = listOf(2L, 5L, 9L)[i / 5], hours = (i % 5).toLong())
            )
        }
        val result = score(catalog.first(), profile = profile, catalog = catalog)
        assertTrue(result.isAi)
        listOf("UPLOADER_VELOCITY", "FARM_PROFILE", "UPLOAD_BURST", "VARIANT_DUPLICATES", "GENRE_HOPPING").forEach {
            assertTrue("missing $it in ${result.reason}", result.reason.contains(it))
        }
    }

    @Test
    fun `an album released in one day is not an upload burst`() {
        val profile = User(
            UPLOADER_ID, "TestArtist", null,
            createdAt = ago(3000), trackCount = 60, followersCount = 5000
        )
        val names = listOf(
            "Opening", "Rivers", "Glass", "Harbour", "Lanterns", "Orchard",
            "Tin Roof", "Undertow", "Kestrel", "Salt", "Wires", "Homecoming"
        )
        val album = names.mapIndexed { i, title ->
            createSampleTrack(id = 5000L + i, title = title, genre = "Indie", createdAt = ago(30, hours = i.toLong()))
        }
        val result = score(album.first(), profile = profile, catalog = album)
        assertFalse(result.isAi)
        assertFalse(result.reason.contains("UPLOAD_BURST"))
    }

    @Test
    fun `listeners calling it AI count, defenders weigh against them`() {
        val accusations = listOf(
            comment(1, "this is AI slop"),
            comment(2, "sounds like suno lol"),
            comment(3, "KI-Müll, sorry"),
            comment(4, "Great track!")
        )
        val accused = score(createSampleTrack(), comments = accusations)
        assertTrue(accused.isAi)
        assertEquals("LISTENER_CALLOUTS", accused.reason)

        val defended = score(createSampleTrack(), comments = accusations + comment(5, "Real singer, not AI"))
        assertFalse(defended.isAi)
    }

    @Test
    fun `a DJ mix does not inherit the uploader's AI verdict`() {
        val labeled = listOf("Rain (Suno v4)", "Fire (Suno v4)", "Ice (suno ai)")
            .mapIndexed { i, title -> createSampleTrack(id = 2100L + i, title = title) }
        val mix = createSampleTrack(title = "cgnfuchur mix 231 - techno - 03.12.2023").copy(durationMs = 62 * 60_000L)
        val result = score(mix, catalog = labeled)
        assertFalse(result.isAi)
        assertTrue(result.reason.contains("LONG_FORM"))

        val song = createSampleTrack(title = "Snow")
        assertTrue(score(song, catalog = labeled).isAi)
    }

    @Test
    fun `AI Gen in the title is a hint`() {
        assertEquals("AI_TERM_MENTION", score(createSampleTrack(title = "A.I GEN")).reason)
    }

    @Test
    fun `the audio fingerprint counts by how sure it is`() {
        // Suno songs heard through SoundCloud measured 0.87-0.89; human tracks 0.000.
        val sure = runBlocking { SoundCloudAiScorer.scoreTrack(createSampleTrack(), audioAiProbability = 0.87f, nowMs = now) }
        assertTrue(sure.isAi)
        assertEquals("AUDIO_FINGERPRINT", sure.reason)

        val likely = runBlocking { SoundCloudAiScorer.scoreTrack(createSampleTrack(), audioAiProbability = 0.71f, nowMs = now) }
        assertFalse(likely.isAi)
        assertEquals(45, likely.score)

        val hint = runBlocking { SoundCloudAiScorer.scoreTrack(createSampleTrack(), audioAiProbability = 0.34f, nowMs = now) }
        assertEquals(25, hint.score)

        val human = runBlocking { SoundCloudAiScorer.scoreTrack(createSampleTrack(), audioAiProbability = 0.02f, nowMs = now) }
        assertEquals(0, human.score)
    }

    @Test
    fun `the offline check sees what needs no network`() {
        val track = createSampleTrack(description = "outlaw country, gritty male vocals, steel guitar, melancholic, slow tempo")
        val offline = SoundCloudAiScorer.scoreOffline(track, nowMs = now)
        assertTrue(offline.isAi)
        assertEquals(score(track).score, offline.score)
        assertFalse(SoundCloudAiScorer.scoreOffline(createSampleTrack(title = "Midnight City"), nowMs = now).isAi)
    }

    @Test
    fun `tracks parsed from SoundCloud JSON get the deep checks`() {
        // Gson skips Kotlin defaults, so tracks from the API arrive with source = null.
        val uploader = User(DEEP_CHECK_UPLOADER_ID, "cgnlike", null)
        val track = createSampleTrack(title = "Kartonherz").copy(user = uploader, source = null)
        val labeled = listOf("Konsequenz (Suno AI)", "Until the Lights Go Out #aimusic", "Mistakes [Suno v4.5]")
            .mapIndexed { i, title -> createSampleTrack(id = 8000L + i, title = title) }
        val api = fakeApi(profile = uploader, catalog = labeled + track, comments = emptyList())

        val result = runBlocking { SoundCloudAiScorer.scoreTrack(track, api = api, nowMs = now) }
        assertTrue(result.isAi)
        assertTrue(result.reason.contains("CATALOG_AI_LABELED"))
    }

    /** Answers the three lookups the deep check makes; any other call fails the test. */
    private fun fakeApi(profile: User, catalog: List<Track>, comments: List<Comment>): SoundCloudApi =
        Proxy.newProxyInstance(SoundCloudApi::class.java.classLoader, arrayOf(SoundCloudApi::class.java)) { _, method, _ ->
            when (method.name) {
                "getUser" -> profile
                "getUserTracks" -> BasicTrackCollection(catalog, null)
                "getTrackComments" -> CommentCollection(comments, null)
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SoundCloudApi

    @Test
    fun `uploader admitting AI in the comments flags the track`() {
        val result = score(createSampleTrack(), comments = listOf(comment(UPLOADER_ID, "yes, made with Suno v4.5 :)")))
        assertTrue(result.isAi)
        assertEquals("UPLOADER_COMMENT", result.reason)
    }

    @Test
    fun `multi-factor scoring flags prompt tags, velocity and engagement`() {
        val track = createSampleTrack(
            title = "Chill Horizon",
            description = "female vocals, cyberpunk synth, driving bass, melancholic melody",
            userCreatedAt = ago(20),
            userTrackCount = 200,
            playbackCount = 10000,
            likesCount = 5,
            repostsCount = 1
        )
        val result = score(track)
        assertTrue(result.isAi)
        assertEquals(99, result.score) // heuristics stop at 99; 100 is reserved for declarations
        assertTrue(result.reason.contains("PROMPT_TAGS"))
        assertTrue(result.reason.contains("UPLOADER_VELOCITY"))
        assertTrue(result.reason.contains("ENGAGEMENT_RATIO"))
        assertTrue(result.reason.contains("CORROBORATED"))
    }

    @Test
    fun `uploads from before generators existed are discounted`() {
        val track = createSampleTrack(
            description = "female vocals, dreamy synthwave, melancholic, 80s",
            createdAt = "2015-06-01T10:00:00Z",
            playbackCount = 10000,
            likesCount = 5,
            repostsCount = 1
        )
        val result = score(track)
        assertFalse(result.isAi)
        assertTrue(result.reason.contains("PRE_AI_ERA"))
    }

    @Test
    fun `verified uploaders are discounted`() {
        val track = createSampleTrack(
            description = "female vocals, dreamy synthwave, melancholic, 80s",
            playbackCount = 10000,
            likesCount = 5,
            repostsCount = 1,
            verified = true
        )
        val result = score(track)
        assertFalse(result.isAi)
        assertTrue(result.reason.contains("VERIFIED_UPLOADER"))
    }

    @Test
    fun `waveform brickwall dynamics detection`() {
        val brickwall = FloatArray(100) { 0.96f + (it % 3) * 0.01f }
        val signals = AiWaveformSignals.analyze(brickwall)
        assertEquals(listOf("WAVEFORM_DYNAMICS"), signals.map { it.code })

        val dynamicWaveform = FloatArray(100) { (it % 10) / 10f }
        assertTrue(AiWaveformSignals.analyze(dynamicWaveform).isEmpty())
    }

    @Test
    fun `full-length waveform shape checks`() {
        val flatAndCut = FloatArray(1800) { 0.93f + (it % 2) * 0.01f }
        val codes = AiWaveformSignals.analyze(flatAndCut).map { it.code }
        assertTrue(codes.containsAll(listOf("WAVEFORM_DYNAMICS", "WAVEFORM_SMEARED", "WAVEFORM_NO_SECTIONS", "WAVEFORM_HARD_CUT")))

        // Fade-in, verse, chorus, breakdown, chorus, fade-out, with a beat every five columns.
        val song = FloatArray(1800) { i ->
            val t = i / 1800f
            val level = when {
                t < 0.05f -> t / 0.05f * 0.6f
                t < 0.30f -> 0.55f
                t < 0.50f -> 0.85f
                t < 0.60f -> 0.30f
                t < 0.90f -> 0.85f
                else -> 0.85f * (1f - (t - 0.90f) / 0.10f)
            }
            (level + if (i % 5 == 0) 0.1f else -0.05f).coerceIn(0.02f, 1f)
        }
        assertTrue(AiWaveformSignals.analyze(song).isEmpty())
    }

    @Test
    fun `organic human track scores zero suspicion`() {
        val track = createSampleTrack(
            title = "Acoustic Sunset (Live Acoustic Session)",
            description = "Recorded live at Abbey Road Studios with full orchestra.",
            tagList = "acoustic, live, guitar",
            userCreatedAt = ago(400),
            userTrackCount = 15,
            playbackCount = 5000,
            likesCount = 350,
            repostsCount = 80
        )
        val dynamicWaveform = FloatArray(100) { (it % 10) / 10f }
        val result = score(track, waveform = dynamicWaveform)
        assertFalse(result.isAi)
        assertEquals(0, result.score)
        assertEquals("NONE", result.reason)
    }

    @Test
    fun `autoplay screening only uses declarations`() {
        assertTrue(SoundCloudAiScorer.isExplicitlyLabeledAi(createSampleTrack(title = "Night Drive (made with Suno)")))
        assertFalse(
            SoundCloudAiScorer.isExplicitlyLabeledAi(
                createSampleTrack(description = "female vocals, cyberpunk synth, driving bass, melancholic melody")
            )
        )
    }

    @Test
    fun `normalizeTrackTitle strips remix and speed modifiers`() {
        assertEquals("Song Title", normalizeTrackTitle("Song Title (Slowed + Reverb)"))
        assertEquals("Song Title", normalizeTrackTitle("Song Title (slowed)"))
        assertEquals("Song Title", normalizeTrackTitle("Song Title - Sped Up"))
        assertEquals("Song Title", normalizeTrackTitle("Song Title (Nightcore)"))
        assertEquals("Song Title", normalizeTrackTitle("Song Title (Hardstyle Edit)"))
        assertEquals("Midnight City", normalizeTrackTitle("Midnight City"))
    }

    private companion object {
        const val UPLOADER_ID = 999L
        const val DEEP_CHECK_UPLOADER_ID = 7777L
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L
    }
}
