package com.alananasss.kittytune

import com.alananasss.kittytune.utils.SoundCloudLocalizationUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundCloudLocalizationTest {

    private val russianStrings = mapOf(
        R.string.home_more_of_what_you_like to "Больше того, что вам нравится",
        R.string.home_mixed_for_user to "Микс для %1\$s",
        R.string.home_mixed_for_you to "Микс для вас",
        R.string.home_trending_by_genre to "В тренде по жанрам",
        R.string.home_trending_by_genre_val to "В тренде по жанрам: %1\$s",
        R.string.home_artists_to_watch to "Перспективные исполнители",
        R.string.home_discover_stations to "Откройте для себя станции",
        R.string.home_made_for_you to "Создано для вас",
        R.string.home_curated_by_soundcloud to "Выбор SoundCloud",
        R.string.home_liked_by_section_title to "Понравилось",
        R.string.home_liked_by_user_title to "Понравилось %1\$s",
    )

    private val resolver: (Int, Array<out Any>) -> String = { resId, args ->
        val format = russianStrings[resId] ?: "res_$resId"
        if (args.isNotEmpty()) {
            String.format(format, *args)
        } else {
            format
        }
    }

    @Test
    fun testMoreOfWhatYouLike() {
        assertEquals("Больше того, что вам нравится", SoundCloudLocalizationUtils.resolveSectionTitle("More of what you like", resolver))
        assertEquals("Больше того, что вам нравится", SoundCloudLocalizationUtils.resolveSectionTitle("more of what you like", resolver))
    }

    @Test
    fun testMixedFor() {
        assertEquals("Микс для Alan", SoundCloudLocalizationUtils.resolveSectionTitle("Mixed for Alan", resolver))
        assertEquals("Микс для вас", SoundCloudLocalizationUtils.resolveSectionTitle("Mixed for you", resolver))
    }

    @Test
    fun testTrendingByGenre() {
        assertEquals("В тренде по жанрам", SoundCloudLocalizationUtils.resolveSectionTitle("Trending by genre", resolver))
        assertEquals("В тренде по жанрам", SoundCloudLocalizationUtils.resolveSectionTitle("Trending by Genre", resolver))
        assertEquals("В тренде по жанрам: Electronic", SoundCloudLocalizationUtils.resolveSectionTitle("Trending by Genre: Electronic", resolver))
    }

    @Test
    fun testArtistsToWatch() {
        assertEquals("Перспективные исполнители", SoundCloudLocalizationUtils.resolveSectionTitle("Artists to watch out for", resolver))
        assertEquals("Перспективные исполнители", SoundCloudLocalizationUtils.resolveSectionTitle("Artists to watch", resolver))
    }

    @Test
    fun testDiscoverWithStations() {
        assertEquals("Откройте для себя станции", SoundCloudLocalizationUtils.resolveSectionTitle("Discover with stations", resolver))
        assertEquals("Откройте для себя станции", SoundCloudLocalizationUtils.resolveSectionTitle("Discover with Stations", resolver))
    }

    @Test
    fun testMadeForYou() {
        assertEquals("Создано для вас", SoundCloudLocalizationUtils.resolveSectionTitle("Made for you", resolver))
    }

    @Test
    fun testCuratedBySoundcloud() {
        assertEquals("Выбор SoundCloud", SoundCloudLocalizationUtils.resolveSectionTitle("Curated by SoundCloud", resolver))
        assertEquals("Выбор SoundCloud", SoundCloudLocalizationUtils.resolveSectionTitle("curated by soundcloud", resolver))
    }

    @Test
    fun testLikedBy() {
        assertEquals("Понравилось Alan", SoundCloudLocalizationUtils.resolveSectionTitle("Liked by Alan", resolver))
        assertEquals("Понравилось", SoundCloudLocalizationUtils.resolveSectionTitle("Liked by", resolver))
        assertEquals("Понравилось", SoundCloudLocalizationUtils.resolveSectionTitle("Liked By", resolver))
    }
}
