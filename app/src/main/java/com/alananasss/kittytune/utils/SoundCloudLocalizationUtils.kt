package com.alananasss.kittytune.utils

import android.content.Context
import com.alananasss.kittytune.R

object SoundCloudLocalizationUtils {

    fun localizeSectionTitle(title: String?, context: Context): String {
        val locContext = try { LocaleUtils.updateBaseContextLocale(context) } catch (e: Exception) { context }
        return resolveSectionTitle(title) { resId, args ->
            if (args.isEmpty()) locContext.getString(resId) else locContext.getString(resId, *args)
        }
    }

    fun resolveSectionTitle(title: String?, stringResolver: (resId: Int, args: Array<out Any>) -> String): String {
        if (title.isNullOrBlank()) return ""
        val trimmed = title.trim()

        // 1. "More of what you like"
        if (trimmed.equals("More of what you like", ignoreCase = true)) {
            return stringResolver(R.string.home_more_of_what_you_like, emptyArray())
        }

        // 2. "Mixed for <user>" or "Mixed for you"
        val mixedForRegex = Regex("""(?i)^Mixed for\s+(.+)$""")
        mixedForRegex.matchEntire(trimmed)?.let { match ->
            val user = match.groupValues[1].trim()
            return if (user.equals("you", ignoreCase = true)) {
                stringResolver(R.string.home_mixed_for_you, emptyArray())
            } else {
                stringResolver(R.string.home_mixed_for_user, arrayOf(user))
            }
        }

        // 3. "Trending by genre" or "Trending by genre: <genre>"
        val trendingByGenreRegex = Regex("""(?i)^Trending by genre(?::\s*(.+))?$""")
        trendingByGenreRegex.matchEntire(trimmed)?.let { match ->
            val genre = match.groupValues.getOrNull(1)?.trim()
            return if (!genre.isNullOrEmpty()) {
                stringResolver(R.string.home_trending_by_genre_val, arrayOf(genre))
            } else {
                stringResolver(R.string.home_trending_by_genre, emptyArray())
            }
        }

        // 4. "Artists to watch out for" / "Artists to watch"
        if (trimmed.matches(Regex("""(?i)^Artists to watch(?: out for)?$"""))) {
            return stringResolver(R.string.home_artists_to_watch, emptyArray())
        }

        // 5. "Discover with stations" / "Discover with Stations"
        if (trimmed.matches(Regex("""(?i)^Discover with [sS]tations$"""))) {
            return stringResolver(R.string.home_discover_stations, emptyArray())
        }

        // 6. "Made for you"
        if (trimmed.equals("Made for you", ignoreCase = true)) {
            return stringResolver(R.string.home_made_for_you, emptyArray())
        }

        // 7. "Curated by SoundCloud" / "Curated by soundcloud"
        if (trimmed.matches(Regex("""(?i)^Curated by [sS]ound[cC]loud$"""))) {
            return stringResolver(R.string.home_curated_by_soundcloud, emptyArray())
        }

        // 8. "Liked by <user>" or "Liked By"
        val likedByRegex = Regex("""(?i)^Liked [bB]y(?:\s+(.+))?$""")
        likedByRegex.matchEntire(trimmed)?.let { match ->
            val user = match.groupValues.getOrNull(1)?.trim()
            return if (!user.isNullOrEmpty()) {
                stringResolver(R.string.home_liked_by_user_title, arrayOf(user))
            } else {
                stringResolver(R.string.home_liked_by_section_title, emptyArray())
            }
        }

        // Standard SoundCloud / KittyTune sections:
        if (trimmed.equals("Recently Played", ignoreCase = true)) {
            return stringResolver(R.string.home_recently_played, emptyArray())
        }
        if (trimmed.equals("Recommended for You", ignoreCase = true) || trimmed.equals("Recommended for you", ignoreCase = true)) {
            return stringResolver(R.string.home_recommended_tracks, emptyArray())
        }
        if (trimmed.equals("Albums for you", ignoreCase = true)) {
            return stringResolver(R.string.home_albums_for_you, emptyArray())
        }
        if (trimmed.equals("Rediscover your collection", ignoreCase = true)) {
            return stringResolver(R.string.home_rediscovery_title, emptyArray())
        }
        if (trimmed.equals("Your Vibe", ignoreCase = true)) {
            return stringResolver(R.string.home_habits_title, emptyArray())
        }
        if (trimmed.equals("Latest from artists you follow", ignoreCase = true)) {
            return stringResolver(R.string.home_stream, emptyArray())
        }
        if (trimmed.equals("New Talent", ignoreCase = true)) {
            return stringResolver(R.string.home_section_new_crew, emptyArray())
        }
        if (trimmed.equals("Top Charts", ignoreCase = true)) {
            return stringResolver(R.string.home_charts, emptyArray())
        }
        if (trimmed.equals("Trending", ignoreCase = true)) {
            return stringResolver(R.string.home_trending, emptyArray())
        }

        val similarToRegex = Regex("""(?i)^Similar to\s+(.+)$""")
        similarToRegex.matchEntire(trimmed)?.let { match ->
            val seed = match.groupValues[1].trim()
            return stringResolver(R.string.home_section_similar, arrayOf(seed))
        }

        val stationRegex = Regex("""(?i)^Station:\s*(.+)$""")
        stationRegex.matchEntire(trimmed)?.let { match ->
            val entity = match.groupValues[1].trim()
            return stringResolver(R.string.home_station_artist_title, arrayOf(entity))
        }

        return title
    }

    fun localizeSectionSubtitle(subtitle: String?, context: Context): String? {
        val locContext = try { LocaleUtils.updateBaseContextLocale(context) } catch (e: Exception) { context }
        val lang = try { LocaleUtils.getLocale(context).language } catch (e: Exception) { "en" }
        return resolveSectionSubtitle(subtitle, lang) { resId -> locContext.getString(resId) }
    }

    fun resolveSectionSubtitle(subtitle: String?, lang: String, stringResolver: (resId: Int) -> String): String? {
        if (subtitle.isNullOrBlank()) return subtitle
        val trimmed = subtitle.trim()

        if (trimmed.equals("Fresh tracks based on your taste", ignoreCase = true)) {
            return stringResolver(R.string.home_discovery_subtitle)
        }
        if (trimmed.equals("Fresh tracks based on your history", ignoreCase = true)) {
            return stringResolver(R.string.home_recommended_tracks_sub)
        }
        if (trimmed.equals("Forgotten gems from your likes", ignoreCase = true)) {
            return stringResolver(R.string.home_rediscovery_sub)
        }
        if (trimmed.equals("Based on your recent listening", ignoreCase = true)) {
            return stringResolver(R.string.home_habits_sub)
        }
        if (trimmed.equals("Artists you might like", ignoreCase = true)) {
            return stringResolver(R.string.home_section_new_crew_sub)
        }
        if (trimmed.equals("Because you listened to this track", ignoreCase = true)) {
            return stringResolver(R.string.home_section_similar_sub)
        }
        if (trimmed.equals("Curated by artists you hear", ignoreCase = true)) {
            return stringResolver(R.string.home_liked_by_section_subtitle)
        }
        if (trimmed.matches(Regex("""(?i)^Curated by [sS]ound[cC]loud$"""))) {
            return stringResolver(R.string.home_curated_by_soundcloud)
        }
        if (trimmed.equals("Artist station", ignoreCase = true)) {
            return stringResolver(R.string.home_artist_station_subtitle)
        }

        if (lang == "ru") {
            if (trimmed.contains("Tracks you might like based on what you've listened to", ignoreCase = true)) {
                return "Треки на основе ваших прослушиваний"
            }
            if (trimmed.contains("Personalized recommendations", ignoreCase = true)) {
                return "Персональные рекомендации"
            }
            if (trimmed.contains("The latest music from artists you follow", ignoreCase = true)) {
                return "Свежая музыка от исполнителей, на которых вы подписаны"
            }
            if (trimmed.contains("Updated every week with fresh music", ignoreCase = true)) {
                return "Обновляется каждую неделю"
            }
            if (trimmed.contains("Popular tracks right now", ignoreCase = true)) {
                return "Популярные треки прямо сейчас"
            }
            if (trimmed.contains("The music you want, when you want it", ignoreCase = true)) {
                return "Музыка для вашего настроения"
            }
        }

        return subtitle
    }
}
