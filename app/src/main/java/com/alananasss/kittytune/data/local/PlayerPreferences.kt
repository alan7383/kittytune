package com.alananasss.kittytune.data.local

import android.content.Context
import android.content.SharedPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.RepeatMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class PlayerBackgroundStyle { THEME, GRADIENT, BLUR }
enum class StartDestination { HOME, LIBRARY }
enum class LyricsAlignment { LEFT, CENTER, RIGHT }

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    FRENCH("fr"),
    ENGLISH("en"),
    HUNGARIAN("hu")
}

class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_TRACK_JSON = "last_track_json"
        private const val KEY_POSITION = "last_position"
        private const val KEY_QUEUE_JSON = "last_queue_full_json"
        private const val KEY_EFFECTS = "audio_effects"
        private const val KEY_CONTEXT_JSON = "last_context_json"
        private const val KEY_SHUFFLE_MODE = "shuffle_mode_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode_state"
        private const val KEY_DOWNLOAD_DIR = "download_directory_uri"
        private const val KEY_AUTOPLAY_STATION = "autoplay_station_enabled"
        private const val KEY_AUDIO_QUALITY = "audio_quality_pref"
        private const val KEY_PERSISTENT_QUEUE = "persistent_queue_enabled"
        private const val KEY_START_DESTINATION = "start_destination_pref"
        private const val KEY_DYNAMIC_THEME = "dynamic_theme_enabled"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_PURE_BLACK = "pure_black_enabled"
        private const val KEY_PLAYER_STYLE = "player_background_style"
        private const val KEY_LOCAL_MEDIA_ENABLED = "local_media_enabled"
        private const val KEY_LOCAL_MEDIA_URIS_SET = "local_media_uris_set_v2"
        private const val KEY_LYRICS_PREFER_LOCAL = "lyrics_prefer_local"
        private const val KEY_LYRICS_ALIGNMENT = "lyrics_alignment"
        private const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        private const val KEY_APP_LANGUAGE = "app_language_code"
        private const val KEY_ACHIEVEMENT_POPUPS = "achievement_popups_enabled"
    }

    fun getAchievementPopupsEnabled(): Boolean = prefs.getBoolean(KEY_ACHIEVEMENT_POPUPS, false) // désactivé par défaut
    fun setAchievementPopupsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ACHIEVEMENT_POPUPS, enabled).apply()

    // --- LANGUAGE MANAGEMENT ---
    fun getAppLanguage(): AppLanguage {
        val code = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.code)
        return AppLanguage.entries.find { it.code == code } ?: AppLanguage.SYSTEM
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
    }

    // --- LYRICS ---

    fun getLyricsPreferLocal(): Boolean = prefs.getBoolean(KEY_LYRICS_PREFER_LOCAL, false)
    fun setLyricsPreferLocal(enabled: Boolean) = prefs.edit().putBoolean(KEY_LYRICS_PREFER_LOCAL, enabled).apply()

    fun getLyricsAlignment(): LyricsAlignment {
        val name = prefs.getString(KEY_LYRICS_ALIGNMENT, LyricsAlignment.CENTER.name)
        return try { LyricsAlignment.valueOf(name!!) } catch (e: Exception) { LyricsAlignment.CENTER }
    }
    fun setLyricsAlignment(align: LyricsAlignment) = prefs.edit().putString(KEY_LYRICS_ALIGNMENT, align.name).apply()

    // Default size: 26f (sp)
    fun getLyricsFontSize(): Float = prefs.getFloat(KEY_LYRICS_FONT_SIZE, 26f)
    fun setLyricsFontSize(size: Float) = prefs.edit().putFloat(KEY_LYRICS_FONT_SIZE, size).apply()
    fun getLocalMediaEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_MEDIA_ENABLED, false)
    fun setLocalMediaEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOCAL_MEDIA_ENABLED, enabled).apply()
    fun getLocalMediaUris(): Set<String> = prefs.getStringSet(KEY_LOCAL_MEDIA_URIS_SET, emptySet()) ?: emptySet()
    fun addLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.add(uri); prefs.edit().putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c).apply() }
    fun removeLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.remove(uri); prefs.edit().putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c).apply() }
    fun getStartDestination(): StartDestination { val n = prefs.getString(KEY_START_DESTINATION, StartDestination.HOME.name); return try { StartDestination.valueOf(n!!) } catch (e: Exception) { StartDestination.HOME } }
    fun setStartDestination(dest: StartDestination) = prefs.edit().putString(KEY_START_DESTINATION, dest.name).apply()
    fun getDynamicTheme(): Boolean = prefs.getBoolean(KEY_DYNAMIC_THEME, true)
    fun setDynamicTheme(enabled: Boolean) = prefs.edit().putBoolean(KEY_DYNAMIC_THEME, enabled).apply()
    fun getThemeMode(): AppThemeMode { val n = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name); return try { AppThemeMode.valueOf(n!!) } catch (e: Exception) { AppThemeMode.SYSTEM } }
    fun setThemeMode(mode: AppThemeMode) = prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    fun getPureBlack(): Boolean = prefs.getBoolean(KEY_PURE_BLACK, false)
    fun setPureBlack(enabled: Boolean) = prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
    fun getPlayerStyle(): PlayerBackgroundStyle { val n = prefs.getString(KEY_PLAYER_STYLE, PlayerBackgroundStyle.BLUR.name); return try { PlayerBackgroundStyle.valueOf(n!!) } catch (e: Exception) { PlayerBackgroundStyle.BLUR } }
    fun setPlayerStyle(style: PlayerBackgroundStyle) = prefs.edit().putString(KEY_PLAYER_STYLE, style.name).apply()
    fun getAutoplayEnabled(): Boolean = prefs.getBoolean(KEY_AUTOPLAY_STATION, true)
    fun setAutoplayEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTOPLAY_STATION, enabled).apply()
    fun getAudioQuality(): String = prefs.getString(KEY_AUDIO_QUALITY, "HIGH") ?: "HIGH"
    fun setAudioQuality(quality: String) = prefs.edit().putString(KEY_AUDIO_QUALITY, quality).apply()
    fun getPersistentQueueEnabled(): Boolean = prefs.getBoolean(KEY_PERSISTENT_QUEUE, true)
    fun setPersistentQueueEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_PERSISTENT_QUEUE, enabled).apply()
    fun savePlaybackState(track: Track?, position: Long, queue: List<Track>, context: PlaybackContext?, shuffleEnabled: Boolean, repeatMode: RepeatMode) {
        if (!getPersistentQueueEnabled()) {
            val editor = prefs.edit()
            editor.putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled); editor.putString(KEY_REPEAT_MODE, repeatMode.name)
            editor.remove(KEY_TRACK_JSON); editor.remove(KEY_QUEUE_JSON); editor.remove(KEY_POSITION); editor.remove(KEY_CONTEXT_JSON)
            editor.apply(); return
        }
        val editor = prefs.edit()
        if (track != null) editor.putString(KEY_TRACK_JSON, gson.toJson(track))
        if (queue.isNotEmpty()) editor.putString(KEY_QUEUE_JSON, gson.toJson(queue))
        editor.putString(KEY_CONTEXT_JSON, gson.toJson(context))
        editor.putLong(KEY_POSITION, position)
        editor.putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
        editor.putString(KEY_REPEAT_MODE, repeatMode.name)
        editor.apply()
    }
    fun saveEffects(state: AudioEffectsState) { prefs.edit().putString(KEY_EFFECTS, gson.toJson(state)).apply() }
    fun saveDownloadLocation(uriString: String?) { val editor = prefs.edit(); if (uriString != null) editor.putString(KEY_DOWNLOAD_DIR, uriString) else editor.remove(KEY_DOWNLOAD_DIR); editor.apply() }
    fun getDownloadLocation(): String? = prefs.getString(KEY_DOWNLOAD_DIR, null)
    fun getLastTrack(): Track? { if (!getPersistentQueueEnabled()) return null; val json = prefs.getString(KEY_TRACK_JSON, null) ?: return null; return try { gson.fromJson(json, Track::class.java) } catch (e: Exception) { null } }
    fun getLastPosition(): Long = prefs.getLong(KEY_POSITION, 0L)
    fun getLastQueue(): List<Track> { if (!getPersistentQueueEnabled()) return emptyList(); val json = prefs.getString(KEY_QUEUE_JSON, null) ?: return emptyList(); val type = object : TypeToken<List<Track>>() {}.type; return try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() } }
    fun getLastContext(): PlaybackContext? { if (!getPersistentQueueEnabled()) return null; val json = prefs.getString(KEY_CONTEXT_JSON, null) ?: return null; return try { gson.fromJson(json, PlaybackContext::class.java) } catch (e: Exception) { null } }
    fun getLastShuffleEnabled(): Boolean = prefs.getBoolean(KEY_SHUFFLE_MODE, false)
    fun getLastRepeatMode(): RepeatMode { val modeName = prefs.getString(KEY_REPEAT_MODE, RepeatMode.NONE.name); return try { RepeatMode.valueOf(modeName ?: RepeatMode.NONE.name) } catch (e: Exception) { RepeatMode.NONE } }
    fun getLastEffects(): AudioEffectsState { val json = prefs.getString(KEY_EFFECTS, null) ?: return AudioEffectsState(); return try { gson.fromJson(json, AudioEffectsState::class.java) } catch (e: Exception) { AudioEffectsState() } }
}