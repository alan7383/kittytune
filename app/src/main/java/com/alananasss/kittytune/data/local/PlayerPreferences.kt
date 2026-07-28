package com.alananasss.kittytune.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.RepeatMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlinx.coroutines.channels.awaitClose

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class PlayerBackgroundStyle { THEME, GRADIENT, BLUR }
enum class StartDestination { HOME, LIBRARY }
enum class LyricsAlignment { LEFT, CENTER, RIGHT }
enum class DiscordStatusDisplay { ACTIVITY, SOUNDCLOUD, ARTIST, SONG }

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    FRENCH("fr"),
    ENGLISH("en"),
    HUNGARIAN("hu")
}

class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val queueFile = File(context.filesDir, "queue_cache.json")

    companion object {
        const val KEY_LISTENING_STATS_ENABLED = "listening_stats_enabled"
        private const val KEY_TRACK_JSON = "last_track_json"
        private const val KEY_POSITION = "last_position"
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
        private const val KEY_PRECISE_SPEED = "precise_speed_enabled"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        private const val KEY_YOUTUBE_FALLBACK = "youtube_fallback_enabled"
        private const val KEY_DOWNLOAD_DRM_STREAMS = "download_drm_streams_enabled"
        private const val KEY_SHOW_LYRICS_BUTTON = "show_lyrics_button_enabled"
        private const val KEY_INLINE_LYRICS = "inline_lyrics_enabled"
        private const val KEY_DISCORD_TOKEN = "discord_token"
        private const val KEY_DISCORD_ENABLED = "discord_rpc_enabled"
        private const val KEY_PRECISE_LYRICS_SEARCH = "precise_lyrics_search_enabled"
        private const val KEY_EARRAPE_WARNING = "has_seen_earrape_warning"

        private const val KEY_DISCORD_ASSET_LOGO = "discord_asset_logo"
        private const val KEY_DISCORD_STATUS_DISPLAY = "discord_status_display"
        private const val KEY_CUSTOM_FONT_ENABLED = "custom_font_enabled"
        private const val KEY_FONT_WGHT = "font_wght"
        private const val KEY_FONT_WDTH = "font_wdth"
        private const val KEY_FONT_SLNT = "font_slnt"
        private const val KEY_FONT_ROND = "font_rond"
        private const val KEY_FONT_GRAD = "font_grad"
        private const val KEY_FONT_OPSZ = "font_opsz"
        private const val KEY_SYNC_LIKES = "sync_likes_enabled"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_KEY_COLOR = "key_color"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_COLOR_SPEC = "color_spec"
        private const val KEY_SLEEP_TIMER_FADE_DURATION = "sleep_timer_fade_duration"
        private const val KEY_SLEEP_TIMER_FADE_ENABLED = "sleep_timer_fade_enabled"

        const val SLEEP_TIMER_FADE_DURATION_MIN = 0
        const val SLEEP_TIMER_FADE_DURATION_MAX = 30
        const val SLEEP_TIMER_FADE_DURATION_DEFAULT = 30
        const val SLEEP_TIMER_FADE_UPDATE_INTERVAL_MS = 50L

        private const val KEY_BOTTOM_MENU_STYLE = "bottom_menu_style"
        private const val KEY_BOTTOM_MENU_ITEMS = "bottom_menu_items_csv"
        private const val KEY_BOTTOM_MENU_FAB = "bottom_menu_fab"
        private const val KEY_BOTTOM_MENU_BLUR = "bottom_menu_blur_enabled"
        private const val KEY_STOP_ON_TASK_CLEAR = "stop_on_task_clear"
        private const val KEY_NEW_PLAYER_DESIGN = "new_player_design_enabled"
    }

    private fun getSafeFloat(key: String, default: Float): Float {
        return try {
            prefs.getFloat(key, default)
        } catch (_: ClassCastException) {
            try {
                val fallback = prefs.getInt(key, default.toInt()).toFloat()
                prefs.edit { putFloat(key, fallback) }
                fallback
            } catch (_: Exception) {
                default
            }
        }
    }

    fun getSyncLikesEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_LIKES, true)
    fun setSyncLikesEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SYNC_LIKES, enabled) }

    fun getCrossfadeEnabled(): Boolean = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
    fun setCrossfadeEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_CROSSFADE_ENABLED, enabled) }

    fun getCrossfadeDuration(): Int = prefs.getInt(KEY_CROSSFADE_DURATION, 5)
    fun setCrossfadeDuration(seconds: Int) = prefs.edit { putInt(KEY_CROSSFADE_DURATION, seconds.coerceIn(1, 12)) }

    fun getCustomFontEnabled() = prefs.getBoolean(KEY_CUSTOM_FONT_ENABLED, true)
    fun setCustomFontEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_CUSTOM_FONT_ENABLED, enabled) }

    fun getFontWght() = prefs.getInt(KEY_FONT_WGHT, 400)
    fun setFontWght(value: Int) = prefs.edit { putInt(KEY_FONT_WGHT, value) }

    fun getFontWdth() = getSafeFloat(KEY_FONT_WDTH, 100f)
    fun setFontWdth(value: Float) = prefs.edit { putFloat(KEY_FONT_WDTH, value) }

    fun getFontSlnt() = getSafeFloat(KEY_FONT_SLNT, 0f)
    fun setFontSlnt(value: Float) = prefs.edit { putFloat(KEY_FONT_SLNT, value) }

    fun getFontRond() = getSafeFloat(KEY_FONT_ROND, 0f)
    fun setFontRond(value: Float) = prefs.edit { putFloat(KEY_FONT_ROND, value) }

    fun getFontGrad() = getSafeFloat(KEY_FONT_GRAD, 0f)
    fun setFontGrad(value: Float) = prefs.edit { putFloat(KEY_FONT_GRAD, value) }

    fun getFontOpsz() = getSafeFloat(KEY_FONT_OPSZ, 14f)
    fun setFontOpsz(value: Float) = prefs.edit { putFloat(KEY_FONT_OPSZ, value) }

    fun getDiscordStatusDisplay(): DiscordStatusDisplay {
        val name = prefs.getString(KEY_DISCORD_STATUS_DISPLAY, DiscordStatusDisplay.ACTIVITY.name)
        return try { DiscordStatusDisplay.valueOf(name!!) } catch (_: Exception) { DiscordStatusDisplay.ACTIVITY }
    }

    fun setDiscordStatusDisplay(display: DiscordStatusDisplay) {
        prefs.edit { putString(KEY_DISCORD_STATUS_DISPLAY, display.name) }
    }

    fun getDiscordToken(): String? = prefs.getString(KEY_DISCORD_TOKEN, null)
    fun setDiscordToken(token: String?) {
        prefs.edit { putString(KEY_DISCORD_TOKEN, token) }
    }

    fun getDiscordRpcEnabled(): Boolean = prefs.getBoolean(KEY_DISCORD_ENABLED, false)
    fun setDiscordRpcEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_DISCORD_ENABLED, enabled) }

    // --- LOGO FUNCTIONS ---
    fun getDiscordAssetLogo(): String? = prefs.getString(KEY_DISCORD_ASSET_LOGO, null)
    fun setDiscordAssetLogo(assetId: String?) {
        prefs.edit { putString(KEY_DISCORD_ASSET_LOGO, assetId) }
    }

    fun getInlineLyricsEnabled(): Boolean = prefs.getBoolean(KEY_INLINE_LYRICS, true)
    fun setInlineLyricsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_INLINE_LYRICS, enabled) }

    fun getShowLyricsButtonEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_LYRICS_BUTTON, true)
    fun setShowLyricsButtonEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SHOW_LYRICS_BUTTON, enabled) }

    fun getYouTubeFallbackEnabled(): Boolean = prefs.getBoolean(KEY_YOUTUBE_FALLBACK, true)
    fun setYouTubeFallbackEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_YOUTUBE_FALLBACK, enabled) }
    
    fun getDownloadDrmStreamsEnabled(): Boolean = prefs.getBoolean(KEY_DOWNLOAD_DRM_STREAMS, true)
    fun setDownloadDrmStreamsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_DOWNLOAD_DRM_STREAMS, enabled) }
    fun getAutoUpdateEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_UPDATE, true)
    fun setAutoUpdateEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTO_UPDATE, enabled) }

    fun getAchievementPopupsEnabled(): Boolean = prefs.getBoolean(KEY_ACHIEVEMENT_POPUPS, false)
    fun setAchievementPopupsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_ACHIEVEMENT_POPUPS, enabled) }

    fun getPreciseSpeedEnabled(): Boolean = prefs.getBoolean(KEY_PRECISE_SPEED, false)
    fun setPreciseSpeedEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_PRECISE_SPEED, enabled) }

    fun getAppLanguage(): AppLanguage {
        val code = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.code)
        return AppLanguage.entries.find { it.code == code } ?: AppLanguage.SYSTEM
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_APP_LANGUAGE, language.code) }
    }

    fun getLyricsPreferLocal(): Boolean = prefs.getBoolean(KEY_LYRICS_PREFER_LOCAL, false)
    fun setLyricsPreferLocal(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_PREFER_LOCAL, enabled) }

    fun getPreciseLyricsSearchEnabled(): Boolean = prefs.getBoolean(KEY_PRECISE_LYRICS_SEARCH, true)
    fun setPreciseLyricsSearchEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_PRECISE_LYRICS_SEARCH, enabled) }

    fun hasSeenEarrapeWarning(): Boolean = prefs.getBoolean(KEY_EARRAPE_WARNING, false)
    fun setHasSeenEarrapeWarning(seen: Boolean) = prefs.edit { putBoolean(KEY_EARRAPE_WARNING, seen) }

    fun getLyricsAlignment(): LyricsAlignment {
        val name = prefs.getString(KEY_LYRICS_ALIGNMENT, LyricsAlignment.CENTER.name)
        return try { LyricsAlignment.valueOf(name!!) } catch (_: Exception) { LyricsAlignment.CENTER }
    }
    fun setLyricsAlignment(align: LyricsAlignment) = prefs.edit { putString(KEY_LYRICS_ALIGNMENT, align.name) }

    fun getLyricsFontSize(): Float = getSafeFloat(KEY_LYRICS_FONT_SIZE, 26f)
    fun setLyricsFontSize(size: Float) = prefs.edit { putFloat(KEY_LYRICS_FONT_SIZE, size) }
    fun getLocalMediaEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_MEDIA_ENABLED, false)
    fun setLocalMediaEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LOCAL_MEDIA_ENABLED, enabled) }
    fun getLocalMediaUris(): Set<String> = prefs.getStringSet(KEY_LOCAL_MEDIA_URIS_SET, emptySet()) ?: emptySet()
    fun addLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.add(uri); prefs.edit { putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c) } }
    fun removeLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.remove(uri); prefs.edit { putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c) } }
    fun getStartDestination(): StartDestination { val n = prefs.getString(KEY_START_DESTINATION, StartDestination.HOME.name); return try { StartDestination.valueOf(n!!) } catch (_: Exception) { StartDestination.HOME } }
    fun setStartDestination(dest: StartDestination) = prefs.edit { putString(KEY_START_DESTINATION, dest.name) }
    fun getDynamicTheme(): Boolean = prefs.getBoolean(KEY_DYNAMIC_THEME, true)
    fun setDynamicTheme(enabled: Boolean) = prefs.edit { putBoolean(KEY_DYNAMIC_THEME, enabled) }
    fun getThemeMode(): AppThemeMode { val n = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name); return try { AppThemeMode.valueOf(n!!) } catch (_: Exception) { AppThemeMode.SYSTEM } }
    fun setThemeMode(mode: AppThemeMode) = prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    fun getPureBlack(): Boolean = prefs.getBoolean(KEY_PURE_BLACK, false)
    fun setPureBlack(enabled: Boolean) = prefs.edit { putBoolean(KEY_PURE_BLACK, enabled) }
    fun getPlayerStyle(): PlayerBackgroundStyle { val n = prefs.getString(KEY_PLAYER_STYLE, PlayerBackgroundStyle.BLUR.name); return try { PlayerBackgroundStyle.valueOf(n!!) } catch (_: Exception) { PlayerBackgroundStyle.BLUR } }
    fun setPlayerStyle(style: PlayerBackgroundStyle) = prefs.edit { putString(KEY_PLAYER_STYLE, style.name) }
    fun getAutoplayEnabled(): Boolean = prefs.getBoolean(KEY_AUTOPLAY_STATION, true)
    fun setAutoplayEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTOPLAY_STATION, enabled) }
    fun getListeningStatsEnabled(): Boolean = prefs.getBoolean(KEY_LISTENING_STATS_ENABLED, true)
    fun setListeningStatsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LISTENING_STATS_ENABLED, enabled) }
    fun getAudioQuality(): String = prefs.getString(KEY_AUDIO_QUALITY, "HIGH") ?: "HIGH"
    fun setAudioQuality(quality: String) = prefs.edit { putString(KEY_AUDIO_QUALITY, quality) }
    fun getPersistentQueueEnabled(): Boolean = prefs.getBoolean(KEY_PERSISTENT_QUEUE, true)
    fun setPersistentQueueEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_PERSISTENT_QUEUE, enabled) }

    fun getKeyColor(): Int = prefs.getInt(KEY_KEY_COLOR, 0)
    fun setKeyColor(color: Int) = prefs.edit { putInt(KEY_KEY_COLOR, color) }

    fun getColorStyle(): String = prefs.getString(KEY_COLOR_STYLE, "System") ?: "System"
    fun setColorStyle(style: String) = prefs.edit { putString(KEY_COLOR_STYLE, style) }

    fun getColorSpec(): String = prefs.getString(KEY_COLOR_SPEC, "SPEC_2025") ?: "SPEC_2025"
    fun setColorSpec(spec: String) = prefs.edit { putString(KEY_COLOR_SPEC, spec) }

    fun getSleepTimerFadeEnabled(): Boolean = prefs.getBoolean(KEY_SLEEP_TIMER_FADE_ENABLED, false)
    fun setSleepTimerFadeEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SLEEP_TIMER_FADE_ENABLED, enabled) }

    fun getSleepTimerFadeDuration(): Int =
        prefs.getInt(KEY_SLEEP_TIMER_FADE_DURATION, SLEEP_TIMER_FADE_DURATION_DEFAULT)

    fun setSleepTimerFadeDuration(seconds: Int) =
        prefs.edit {
            putInt(
                KEY_SLEEP_TIMER_FADE_DURATION,
                seconds.coerceIn(
                    SLEEP_TIMER_FADE_DURATION_MIN,
                    SLEEP_TIMER_FADE_DURATION_MAX,
                ),
            )
        }

    fun getBottomMenuStyle(): String = prefs.getString(KEY_BOTTOM_MENU_STYLE, "modern") ?: "modern"
    fun setBottomMenuStyle(style: String) = prefs.edit { putString(KEY_BOTTOM_MENU_STYLE, style) }
    fun bottomMenuStyleFlow(): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BOTTOM_MENU_STYLE) trySend(getBottomMenuStyle())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getBottomMenuStyle())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getBottomMenuItems(): List<String> {
        val defaultItems = "home,search,genres,library"
        val csv = prefs.getString(KEY_BOTTOM_MENU_ITEMS, defaultItems) ?: defaultItems
        return csv.split(",").filter { it.isNotBlank() }
    }
    fun setBottomMenuItems(items: List<String>) = prefs.edit { putString(KEY_BOTTOM_MENU_ITEMS, items.joinToString(",")) }
    fun bottomMenuItemsFlow(): kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BOTTOM_MENU_ITEMS) trySend(getBottomMenuItems())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getBottomMenuItems())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getBottomMenuFab(): String = prefs.getString(KEY_BOTTOM_MENU_FAB, "settings") ?: "settings"
    fun setBottomMenuFab(fab: String) = prefs.edit { putString(KEY_BOTTOM_MENU_FAB, fab) }
    fun bottomMenuFabFlow(): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BOTTOM_MENU_FAB) trySend(getBottomMenuFab())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getBottomMenuFab())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getBottomMenuBlurEnabled(): Boolean = prefs.getBoolean(KEY_BOTTOM_MENU_BLUR, true)
    fun setBottomMenuBlurEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_BOTTOM_MENU_BLUR, enabled) }

    fun getStopOnTaskClear(): Boolean = prefs.getBoolean(KEY_STOP_ON_TASK_CLEAR, true)
    fun setStopOnTaskClear(enabled: Boolean) = prefs.edit { putBoolean(KEY_STOP_ON_TASK_CLEAR, enabled) }
    fun getNewPlayerDesignEnabled(): Boolean = prefs.getBoolean(KEY_NEW_PLAYER_DESIGN, true)
    fun setNewPlayerDesignEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_NEW_PLAYER_DESIGN, enabled) }
    fun bottomMenuBlurFlow(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BOTTOM_MENU_BLUR) trySend(getBottomMenuBlurEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getBottomMenuBlurEnabled())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun savePlaybackState(track: Track?, position: Long, queue: List<Track>, context: PlaybackContext?, shuffleEnabled: Boolean, repeatMode: RepeatMode) {
        if (!getPersistentQueueEnabled()) {
            prefs.edit {
                putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
                putString(KEY_REPEAT_MODE, repeatMode.name)
                remove(KEY_TRACK_JSON)
                if (queueFile.exists()) queueFile.delete()
                remove(KEY_POSITION)
                remove(KEY_CONTEXT_JSON)
            }
            return
        }
        
        if (queue.isNotEmpty()) {
            try {
                FileWriter(queueFile).use { writer ->
                    gson.toJson(queue, writer)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        prefs.edit {
            track?.let { putString(KEY_TRACK_JSON, gson.toJson(it)) }
            putString(KEY_CONTEXT_JSON, gson.toJson(context))
            putLong(KEY_POSITION, position)
            putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
            putString(KEY_REPEAT_MODE, repeatMode.name)
        }
    }
    fun saveEffects(state: AudioEffectsState) { prefs.edit { putString(KEY_EFFECTS, gson.toJson(state)) } }
    fun saveDownloadLocation(uriString: String?) { if (uriString != null) prefs.edit { putString(KEY_DOWNLOAD_DIR, uriString) } else prefs.edit { remove(KEY_DOWNLOAD_DIR) } }
    fun getDownloadLocation(): String? = prefs.getString(KEY_DOWNLOAD_DIR, null)
    fun getLastTrack(): Track? { if (!getPersistentQueueEnabled()) return null; val json = prefs.getString(KEY_TRACK_JSON, null) ?: return null; return try { gson.fromJson(json, Track::class.java) } catch (_: Exception) { null } }
    fun getLastPosition(): Long = prefs.getLong(KEY_POSITION, 0L)
    fun getLastQueue(): List<Track> {
        if (!getPersistentQueueEnabled()) return emptyList()
        if (queueFile.exists()) {
            return try {
                val type = object : TypeToken<List<Track>>() {}.type
                FileReader(queueFile).use { reader ->
                    gson.fromJson(reader, type) ?: emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val json = prefs.getString("last_queue_full_json", null) ?: return emptyList()
        val type = object : TypeToken<List<Track>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
    fun getLastContext(): PlaybackContext? { if (!getPersistentQueueEnabled()) return null; val json = prefs.getString(KEY_CONTEXT_JSON, null) ?: return null; return try { gson.fromJson(json, PlaybackContext::class.java) } catch (_: Exception) { null } }
    fun getLastShuffleEnabled(): Boolean = prefs.getBoolean(KEY_SHUFFLE_MODE, false)
    fun getLastRepeatMode(): RepeatMode { val modeName = prefs.getString(KEY_REPEAT_MODE, RepeatMode.NONE.name); return try { RepeatMode.valueOf(modeName ?: RepeatMode.NONE.name) } catch (_: Exception) { RepeatMode.NONE } }
    fun getLastEffects(): AudioEffectsState { val json = prefs.getString(KEY_EFFECTS, null) ?: return AudioEffectsState(); return try { gson.fromJson(json, AudioEffectsState::class.java) } catch (_: Exception) { AudioEffectsState() } }
}
