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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.data.lyrics.providers.DefaultLyricsProviderOrder
import com.alananasss.kittytune.data.lyrics.providers.deserializeLyricsProviderOrder
import com.alananasss.kittytune.data.lyrics.providers.serializeLyricsProviderOrder
import com.alananasss.kittytune.data.lyrics.clients.PaxsenixClient

import androidx.annotation.StringRes
import com.alananasss.kittytune.R

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class PlayerBackgroundStyle { THEME, GRADIENT, BLUR, APPLE_MUSIC }
enum class StartDestination { HOME, LIBRARY }
enum class LyricsAlignment { LEFT, CENTER, RIGHT }
enum class LyricsUiStyle {
    ENHANCED,
    CLASSIC;

    companion object {
        fun fromString(name: String?): LyricsUiStyle = when (name) {
            "CLASSIC" -> CLASSIC
            else -> ENHANCED
        }
    }
}
enum class LyricsFont { APPLE, APP_DEFAULT }
enum class DiscordStatusDisplay { ACTIVITY, SOUNDCLOUD, ARTIST, SONG }

enum class PlayerProgressMode { SOUNDCLOUD, HYBRID_WAVEFORM, CLASSIC_BAR }
enum class PlayerSliderStyle { BAR, WAVY, SLIM, SQUIGGLY }
enum class PlayerDesign { PIXEL_PLAYER, SOUNDCLOUD, MODERN, CLASSIC }
enum class LyricsUnderCoverPlacement { REPLACE_TITLE_ARTIST, ABOVE_TITLE_ARTIST }
enum class LyricsDisplayState { OFF, UNDER_COVER, COVER_REPLACED }

enum class PlayerActionButtonSlot(@StringRes val titleRes: Int) {
    LIKE(R.string.slot_like),
    COMMENTS(R.string.slot_comments),
    SHARE(R.string.slot_share),
    QUEUE(R.string.slot_queue),
    AUDIO_FX(R.string.slot_audio_fx),
    SHUFFLE(R.string.slot_shuffle),
    REPEAT(R.string.slot_repeat),
    LYRICS(R.string.slot_lyrics),
    SLEEP_TIMER(R.string.slot_sleep_timer),
    HAPTICS(R.string.slot_haptics),
    MORE(R.string.slot_more),
    NONE(R.string.slot_none)
}

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    FRENCH("fr"),
    ENGLISH("en"),
    HUNGARIAN("hu"),
    RUSSIAN("ru"),
    VIETNAMESE("vi")
}

enum class TrackRemovalMethod {
    SWIPE_AND_MENU,
    MENU_ONLY
}

class PlayerPreferences(context: Context) {
    private val context: Context = context
    private val prefs: SharedPreferences = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
    private val gson = com.alananasss.kittytune.utils.AppUtils.gson
    private val queueFile = File(context.filesDir, "queue_cache.json")

    companion object {
        const val KEY_PLAYER_PROGRESS_MODE = "player_progress_mode"
        const val KEY_PLAYER_SLIDER_STYLE = "player_slider_style"
        const val KEY_WAVEFORM_COMMENTS_POPUP = "waveform_comments_popup_enabled"
        const val KEY_SOUNDCLOUD_REACTIONS_BAR = "soundcloud_reactions_bar_enabled"
        const val KEY_SOUNDCLOUD_PARALLAX = "soundcloud_parallax_enabled"
        const val KEY_SOUNDCLOUD_SLOT_PREFIX = "soundcloud_slot_"
        const val KEY_CLASSIC_SLOT_PREFIX = "classic_slot_"
        const val KEY_PIXEL_SLOT_PREFIX = "pixel_slot_"
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
        private const val KEY_TRACK_DYNAMIC_THEME = "track_dynamic_theme_enabled"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_PURE_BLACK = "pure_black_enabled"
        const val KEY_PLAYER_STYLE = "player_background_style"
        private const val KEY_LOCAL_MEDIA_ENABLED = "local_media_enabled"
        private const val KEY_LOCAL_MEDIA_URIS_SET = "local_media_uris_set_v2"
        private const val KEY_LYRICS_PREFER_LOCAL = "lyrics_prefer_local"
        private const val KEY_LYRICS_ALIGNMENT = "lyrics_alignment"
        private const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        private const val KEY_APP_LANGUAGE = "app_language_code"
        private const val KEY_APP_ICON = "app_icon_id"
        private const val KEY_ACHIEVEMENT_POPUPS = "achievement_popups_enabled"
        private const val KEY_PRECISE_SPEED = "precise_speed_enabled"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        private const val KEY_YOUTUBE_FALLBACK = "youtube_fallback_enabled"
        private const val KEY_DOWNLOAD_DRM_STREAMS = "download_drm_streams_enabled"
        private const val KEY_SHOW_LYRICS_BUTTON = "show_lyrics_button_enabled"
        private const val KEY_INLINE_LYRICS = "inline_lyrics_enabled"
        private const val KEY_LYRICS_UNDER_COVER_ENABLED = "lyrics_under_cover_enabled"
        private const val KEY_LYRICS_MULTI_STATE_TOGGLE = "lyrics_multi_state_toggle"
        private const val KEY_LYRICS_UNDER_COVER_PLACEMENT = "lyrics_under_cover_placement"
        private const val KEY_LYRICS_UNDER_COVER_ALWAYS_VISIBLE = "lyrics_under_cover_always_visible"
        private const val KEY_DISCORD_TOKEN = "discord_token"
        private const val KEY_DISCORD_ENABLED = "discord_rpc_enabled"
        private const val KEY_PRECISE_LYRICS_SEARCH = "precise_lyrics_search_enabled"
        private const val KEY_EARRAPE_WARNING = "has_seen_earrape_warning"
        private const val KEY_SAVE_POSITION = "save_position_enabled"
        private const val KEY_SOUNDCLOUD_HISTORY_SYNC = "soundcloud_history_sync_enabled"
        private const val KEY_PINNED_AUDIO_FX = "pinned_audio_fx_list"
        val DEFAULT_PINNED_AUDIO_FX = listOf("bass_boost", "earrape", "eight_d", "muffled", "reverb", "rain")
        private const val KEY_ANDROID_AUTO_SYNCED_LYRICS = "android_auto_synced_lyrics"
        const val KEY_ANIMATED_COVERS_ENABLED = "animated_covers_enabled"
        const val KEY_ANIMATED_COVERS_FADE_UI = "animated_covers_fade_ui"
        const val KEY_ANIMATED_ARTIST_PROFILES_ENABLED = "animated_artist_profiles_enabled"

        private const val KEY_LYRICS_PROVIDER = "lyrics_provider"
        private const val KEY_LYRICS_PROVIDER_ORDER = "lyrics_provider_order"
        private const val KEY_PAXSENIX_API_KEY = "paxsenix_api_key"
        private const val KEY_ENABLE_PROVIDER_PREFIX = "enable_lyrics_provider_"
        private const val KEY_LYRICS_TRANSLATION = "lyrics_translation_enabled"
        private const val KEY_LYRICS_TRANSLATION_LANG = "lyrics_translation_lang"
        private const val KEY_LYRICS_ROMANIZATION = "lyrics_romanization_enabled"
        private const val KEY_LYRICS_WORD_SYNC = "lyrics_word_sync_enabled"
        private const val KEY_LYRICS_APPLE_EFFECT = "lyrics_apple_effect_enabled"
        private const val KEY_LYRICS_UI_STYLE = "lyrics_ui_style"
        private const val KEY_LYRICS_LINE_BLUR = "lyrics_line_blur_enabled"
        private const val KEY_LYRICS_LRC_BOUNCE_ENABLED = "lyrics_lrc_bounce_enabled"
        private const val KEY_LYRICS_BOUNCE_FACTOR = "lyrics_bounce_factor"
        private const val KEY_LYRICS_GLOW_FACTOR = "lyrics_glow_factor"
        private const val KEY_LYRICS_FILL_TRANSITION_WIDTH = "lyrics_fill_transition_width"
        private const val KEY_LYRICS_LINE_SPACING = "lyrics_line_spacing"
        private const val KEY_LYRICS_FONT = "lyrics_font"
        private const val KEY_LYRICS_DUET_VIEW = "lyrics_duet_view"

        private const val KEY_DISCORD_ASSET_LOGO = "discord_asset_logo"
        private const val KEY_DISCORD_STATUS_DISPLAY = "discord_status_display"
        private const val KEY_CUSTOM_FONT_ENABLED = "custom_font_enabled"
        private const val KEY_FONT_WGHT = "font_wght"
        private const val KEY_FONT_WDTH = "font_wdth"
        private const val KEY_FONT_SLNT = "font_slnt"
        private const val KEY_FONT_ROND = "font_rond"
        private const val KEY_FONT_GRAD = "font_grad"
        private const val KEY_FONT_OPSZ = "font_opsz"
        private const val KEY_SYNC_DISCLAIMER_DISMISSED = "sync_disclaimer_dismissed"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_CROSSFADE_GAPLESS = "crossfade_gapless"
        const val KEY_AUTOMIX_ENABLED = "automix_enabled"
        const val KEY_AUTOMIX_DEBUG_OVERLAY = "automix_debug_overlay"
        private const val KEY_AUTOMIX_TEMPO_MATCH = "automix_tempo_match"
        private const val KEY_AUTOMIX_HARMONIC_MIX = "automix_harmonic_mix"
        private const val KEY_AUTOMIX_DYNAMIC_MIX_POINTS = "automix_dynamic_mix_points"
        private const val KEY_AUTOMIX_BASS_DUCKING = "automix_bass_ducking"
        private const val KEY_AUTOMIX_OVERLAP_MODE = "automix_overlap_mode"
        private const val KEY_CACHED_USER_ID = "cached_user_id"
        private const val KEY_CACHED_USERNAME = "cached_username"
        private const val KEY_KEY_COLOR = "key_color"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_COLOR_SPEC = "color_spec"
        private const val KEY_SYNC_LIKES = "sync_likes_enabled"
        private const val KEY_SLEEP_TIMER_FADE_DURATION = "sleep_timer_fade_duration"
        private const val KEY_SLEEP_TIMER_FADE_ENABLED = "sleep_timer_fade_enabled"

        const val KEY_PROXY_ENABLED = "proxy_enabled"
        const val KEY_PROXY_TYPE = "proxy_type"
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
        const val KEY_PROXY_AUTH_ENABLED = "proxy_auth_enabled"
        const val KEY_PROXY_USERNAME = "proxy_username"
        const val KEY_PROXY_PASSWORD = "proxy_password"
        const val KEY_PROXY_PROFILES = "saved_proxy_profiles_json"
        const val KEY_SELECTED_PROXY_PROFILE_ID = "selected_proxy_profile_id"

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
        const val KEY_PLAYER_DESIGN = "player_design"
        private const val KEY_WAVEFORM_COMMENTS = "waveform_comments_enabled"
        private const val KEY_TRACK_REMOVAL_METHOD = "track_removal_method"

        const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_HAPTICS_STRENGTH = "haptics_strength"
        const val KEY_HAPTICS_PLAY_PAUSE = "haptics_play_pause"
        const val KEY_HAPTICS_SEEK = "haptics_seek"
        const val KEY_HAPTICS_LIKE = "haptics_like"
        const val KEY_HAPTICS_QUEUE = "haptics_queue"

        const val KEY_AUDIO_PROVIDER_ORDER = "audio_provider_order"
        const val KEY_QOBUZ_COUNTRY = "qobuz_country"
        const val KEY_QOBUZ_CUSTOM_INSTANCES = "qobuz_custom_instances"
        const val KEY_QOBUZ_QUALITY = "qobuz_quality"
        const val KEY_TIDAL_RESOLVER_ENDPOINTS = "tidal_resolver_endpoints"
        const val KEY_TIDAL_AUDIO_QUALITY = "tidal_audio_quality"
        const val KEY_TIDAL_COOKIE = "tidal_cookie"
        const val KEY_DEEZER_RESOLVER_URL = "deezer_resolver_url"
        const val KEY_DEEZER_AUDIO_QUALITY = "deezer_audio_quality"
        const val KEY_DEEZER_FAST_MODE = "deezer_fast_mode"
        const val KEY_DEEZER_PROXY_MODE = "deezer_proxy_mode"
        const val KEY_DEEZER_PROXY_URL = "deezer_proxy_url"
        const val KEY_DEEZER_COOKIE = "deezer_cookie"
        const val KEY_DEEZER_USE_ACCOUNT = "deezer_use_account"
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

    fun isSyncDisclaimerDismissed(): Boolean = prefs.getBoolean(KEY_SYNC_DISCLAIMER_DISMISSED, false)
    fun setSyncDisclaimerDismissed(dismissed: Boolean) = prefs.edit { putBoolean(KEY_SYNC_DISCLAIMER_DISMISSED, dismissed) }

    fun getSyncLikesEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_LIKES, true)
    fun setSyncLikesEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SYNC_LIKES, enabled) }

    fun getCrossfadeEnabled(): Boolean = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
    fun setCrossfadeEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_CROSSFADE_ENABLED, enabled) }

    fun getCrossfadeDuration(): Int = prefs.getInt(KEY_CROSSFADE_DURATION, 5)
    fun setCrossfadeDuration(seconds: Int) = prefs.edit { putInt(KEY_CROSSFADE_DURATION, seconds.coerceIn(1, 12)) }

    fun getCrossfadeGapless(): Boolean = prefs.getBoolean(KEY_CROSSFADE_GAPLESS, true)
    fun setCrossfadeGapless(enabled: Boolean) = prefs.edit { putBoolean(KEY_CROSSFADE_GAPLESS, enabled) }

    fun getAutomixEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMIX_ENABLED, false)
    fun setAutomixEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTOMIX_ENABLED, enabled) }

    fun getAutomixDebugOverlayEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMIX_DEBUG_OVERLAY, false)
    fun setAutomixDebugOverlayEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTOMIX_DEBUG_OVERLAY, enabled) }

    fun getAutomixTempoMatchEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMIX_TEMPO_MATCH, true)
    fun setAutomixTempoMatchEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTOMIX_TEMPO_MATCH, enabled) }

    fun getAutomixHarmonicMixEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMIX_HARMONIC_MIX, true)
    fun setAutomixHarmonicMixEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTOMIX_HARMONIC_MIX, enabled) }

    fun getAutomixDynamicMixPointsEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMIX_DYNAMIC_MIX_POINTS, true)
    fun setAutomixDynamicMixPointsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTOMIX_DYNAMIC_MIX_POINTS, enabled) }

    fun getAutomixBassDuckingEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMIX_BASS_DUCKING, true)
    fun setAutomixBassDuckingEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_AUTOMIX_BASS_DUCKING, enabled) }

    fun getAutomixOverlapMode(): Int = prefs.getInt(KEY_AUTOMIX_OVERLAP_MODE, 0)
    fun setAutomixOverlapMode(mode: Int) = prefs.edit { putInt(KEY_AUTOMIX_OVERLAP_MODE, mode) }

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
        return try {
            DiscordStatusDisplay.valueOf(name!!)
        } catch (_: Exception) {
            DiscordStatusDisplay.ACTIVITY
        }
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

    fun getDiscordAssetLogo(): String? = prefs.getString(KEY_DISCORD_ASSET_LOGO, null)
    fun setDiscordAssetLogo(assetId: String?) {
        prefs.edit { putString(KEY_DISCORD_ASSET_LOGO, assetId) }
    }

    fun getInlineLyricsEnabled(): Boolean = prefs.getBoolean(KEY_INLINE_LYRICS, false)
    fun setInlineLyricsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_INLINE_LYRICS, enabled) }

    fun getLyricsUnderCoverEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_UNDER_COVER_ENABLED, true)
    fun setLyricsUnderCoverEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_UNDER_COVER_ENABLED, enabled) }

    fun getLyricsMultiStateToggle(): Boolean = prefs.getBoolean(KEY_LYRICS_MULTI_STATE_TOGGLE, true)
    fun setLyricsMultiStateToggle(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_MULTI_STATE_TOGGLE, enabled) }

    fun getLyricsUnderCoverPlacement(): LyricsUnderCoverPlacement {
        val name = prefs.getString(KEY_LYRICS_UNDER_COVER_PLACEMENT, LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST.name)
        return runCatching { LyricsUnderCoverPlacement.valueOf(name ?: "") }.getOrDefault(LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST)
    }
    fun setLyricsUnderCoverPlacement(placement: LyricsUnderCoverPlacement) =
        prefs.edit { putString(KEY_LYRICS_UNDER_COVER_PLACEMENT, placement.name) }

    fun getLyricsUnderCoverAlwaysVisible(): Boolean = prefs.getBoolean(KEY_LYRICS_UNDER_COVER_ALWAYS_VISIBLE, false)
    fun setLyricsUnderCoverAlwaysVisible(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_UNDER_COVER_ALWAYS_VISIBLE, enabled) }

    fun getShowLyricsButtonEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_LYRICS_BUTTON, true)
    fun setShowLyricsButtonEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SHOW_LYRICS_BUTTON, enabled) }

    fun getYouTubeFallbackEnabled(): Boolean = prefs.getBoolean(KEY_YOUTUBE_FALLBACK, true)
    fun setYouTubeFallbackEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_YOUTUBE_FALLBACK, enabled) }

    fun getDownloadDrmStreamsEnabled(): Boolean = prefs.getBoolean(KEY_DOWNLOAD_DRM_STREAMS, true)
    fun setDownloadDrmStreamsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_DOWNLOAD_DRM_STREAMS, enabled) }

    fun getHapticsEnabled(): Boolean = prefs.getBoolean(KEY_HAPTICS_ENABLED, false)
    fun setHapticsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_HAPTICS_ENABLED, enabled) }

    fun getHapticsStrength(): Float = getSafeFloat(KEY_HAPTICS_STRENGTH, 80f)
    fun setHapticsStrength(strength: Float) = prefs.edit { putFloat(KEY_HAPTICS_STRENGTH, strength.coerceIn(0f, 100f)) }

    fun getHapticsPlayPause(): Boolean = prefs.getBoolean(KEY_HAPTICS_PLAY_PAUSE, true)
    fun setHapticsPlayPause(enabled: Boolean) = prefs.edit { putBoolean(KEY_HAPTICS_PLAY_PAUSE, enabled) }

    fun getHapticsSeek(): Boolean = prefs.getBoolean(KEY_HAPTICS_SEEK, true)
    fun setHapticsSeek(enabled: Boolean) = prefs.edit { putBoolean(KEY_HAPTICS_SEEK, enabled) }

    fun getHapticsLike(): Boolean = prefs.getBoolean(KEY_HAPTICS_LIKE, true)
    fun setHapticsLike(enabled: Boolean) = prefs.edit { putBoolean(KEY_HAPTICS_LIKE, enabled) }

    fun getHapticsQueue(): Boolean = prefs.getBoolean(KEY_HAPTICS_QUEUE, true)
    fun setHapticsQueue(enabled: Boolean) = prefs.edit { putBoolean(KEY_HAPTICS_QUEUE, enabled) }

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

    fun getAppIconId(): String = prefs.getString(KEY_APP_ICON, "default") ?: "default"

    fun setAppIconId(id: String) {
        prefs.edit { putString(KEY_APP_ICON, id) }
    }

    fun getLyricsPreferLocal(): Boolean = prefs.getBoolean(KEY_LYRICS_PREFER_LOCAL, false)
    fun setLyricsPreferLocal(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_PREFER_LOCAL, enabled) }

    fun getPreciseLyricsSearchEnabled(): Boolean = prefs.getBoolean(KEY_PRECISE_LYRICS_SEARCH, true)
    fun setPreciseLyricsSearchEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_PRECISE_LYRICS_SEARCH, enabled) }

    fun getAndroidAutoSyncedLyricsEnabled(): Boolean = prefs.getBoolean(KEY_ANDROID_AUTO_SYNCED_LYRICS, true)
    fun setAndroidAutoSyncedLyricsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_ANDROID_AUTO_SYNCED_LYRICS, enabled) }

    fun getAndroidAutoSyncedLyricsFlow(): Flow<Boolean> = callbackFlow {
        trySend(getAndroidAutoSyncedLyricsEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ANDROID_AUTO_SYNCED_LYRICS) {
                trySend(getAndroidAutoSyncedLyricsEnabled())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getAnimatedCoversEnabled(): Boolean = prefs.getBoolean(KEY_ANIMATED_COVERS_ENABLED, true)
    fun setAnimatedCoversEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_ANIMATED_COVERS_ENABLED, enabled) }

    fun getAnimatedCoversFlow(): Flow<Boolean> = callbackFlow {
        trySend(getAnimatedCoversEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ANIMATED_COVERS_ENABLED) {
                trySend(getAnimatedCoversEnabled())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getAnimatedCoversFadeUiEnabled(): Boolean = prefs.getBoolean(KEY_ANIMATED_COVERS_FADE_UI, false)
    fun setAnimatedCoversFadeUiEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_ANIMATED_COVERS_FADE_UI, enabled) }

    fun getAnimatedCoversFadeUiFlow(): Flow<Boolean> = callbackFlow {
        trySend(getAnimatedCoversFadeUiEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ANIMATED_COVERS_FADE_UI) {
                trySend(getAnimatedCoversFadeUiEnabled())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getAnimatedArtistProfilesEnabled(): Boolean = prefs.getBoolean(KEY_ANIMATED_ARTIST_PROFILES_ENABLED, true)
    fun setAnimatedArtistProfilesEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_ANIMATED_ARTIST_PROFILES_ENABLED, enabled) }

    fun getAnimatedArtistProfilesFlow(): Flow<Boolean> = callbackFlow {
        trySend(getAnimatedArtistProfilesEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ANIMATED_ARTIST_PROFILES_ENABLED) {
                trySend(getAnimatedArtistProfilesEnabled())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun hasSeenEarrapeWarning(): Boolean = prefs.getBoolean(KEY_EARRAPE_WARNING, false)
    fun setHasSeenEarrapeWarning(seen: Boolean) = prefs.edit { putBoolean(KEY_EARRAPE_WARNING, seen) }

    fun getLyricsAlignment(): LyricsAlignment {
        val name = prefs.getString(KEY_LYRICS_ALIGNMENT, LyricsAlignment.CENTER.name)
        return try {
            LyricsAlignment.valueOf(name!!)
        } catch (_: Exception) {
            LyricsAlignment.CENTER
        }
    }

    fun setLyricsAlignment(align: LyricsAlignment) = prefs.edit { putString(KEY_LYRICS_ALIGNMENT, align.name) }

    fun getLyricsFontSize(): Float = getSafeFloat(KEY_LYRICS_FONT_SIZE, 26f)
    fun setLyricsFontSize(size: Float) = prefs.edit { putFloat(KEY_LYRICS_FONT_SIZE, size) }

    fun getLyricsProvider(): com.alananasss.kittytune.ui.player.LyricsProvider {
        val name =
            prefs.getString(KEY_LYRICS_PROVIDER, com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY.name)
        return try {
            com.alananasss.kittytune.ui.player.LyricsProvider.valueOf(name!!)
        } catch (_: Exception) {
            com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY
        }
    }

    fun setLyricsProvider(provider: com.alananasss.kittytune.ui.player.LyricsProvider) =
        prefs.edit { putString(KEY_LYRICS_PROVIDER, provider.name) }

    fun getLyricsProviderOrder(): List<PreferredLyricsProvider> {
        val raw = prefs.getString(KEY_LYRICS_PROVIDER_ORDER, null)
        return deserializeLyricsProviderOrder(raw)
    }

    fun setLyricsProviderOrder(order: List<PreferredLyricsProvider>) {
        prefs.edit { putString(KEY_LYRICS_PROVIDER_ORDER, serializeLyricsProviderOrder(order)) }
    }

    fun getLyricsProviderEnabled(provider: PreferredLyricsProvider): Boolean {
        return prefs.getBoolean(KEY_ENABLE_PROVIDER_PREFIX + provider.name.lowercase(), true)
    }

    fun setLyricsProviderEnabled(provider: PreferredLyricsProvider, enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLE_PROVIDER_PREFIX + provider.name.lowercase(), enabled) }
    }

    fun getPaxsenixApiKey(): String {
        val key = prefs.getString(KEY_PAXSENIX_API_KEY, "") ?: ""
        PaxsenixClient.setApiKey(key)
        return key
    }

    fun setPaxsenixApiKey(key: String) {
        PaxsenixClient.setApiKey(key)
        prefs.edit { putString(KEY_PAXSENIX_API_KEY, key) }
    }

    fun getLyricsTranslationEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_TRANSLATION, false)
    fun setLyricsTranslationEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_TRANSLATION, enabled) }

    fun getLyricsTranslationLang(): String {
        val code = prefs.getString(KEY_LYRICS_TRANSLATION_LANG, null)
        if (code != null) return code
        val appLang = getAppLanguage()
        if (appLang != AppLanguage.SYSTEM) return appLang.code
        return java.util.Locale.getDefault().language.take(2).lowercase()
    }

    fun setLyricsTranslationLang(lang: String) = prefs.edit { putString(KEY_LYRICS_TRANSLATION_LANG, lang) }

    fun getLyricsRomanizationEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_ROMANIZATION, false)
    fun setLyricsRomanizationEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_ROMANIZATION, enabled) }

    fun getLyricsWordSyncEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_WORD_SYNC, true)
    fun setLyricsWordSyncEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_WORD_SYNC, enabled) }

    fun getLyricsAppleEffectEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_APPLE_EFFECT, true)
    fun setLyricsAppleEffectEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_APPLE_EFFECT, enabled) }

    fun getLyricsUiStyle(): LyricsUiStyle {
        val name = prefs.getString(KEY_LYRICS_UI_STYLE, LyricsUiStyle.ENHANCED.name)
        return LyricsUiStyle.fromString(name)
    }
    fun setLyricsUiStyle(style: LyricsUiStyle) = prefs.edit { putString(KEY_LYRICS_UI_STYLE, style.name) }

    fun getLyricsLineBlurEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_LINE_BLUR, true)
    fun setLyricsLineBlurEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_LINE_BLUR, enabled) }

    fun getLyricsLrcBounceEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_LRC_BOUNCE_ENABLED, true)
    fun setLyricsLrcBounceEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_LRC_BOUNCE_ENABLED, enabled) }

    fun getLyricsBounceFactor(): Float = getSafeFloat(KEY_LYRICS_BOUNCE_FACTOR, 1.0f)
    fun setLyricsBounceFactor(factor: Float) = prefs.edit { putFloat(KEY_LYRICS_BOUNCE_FACTOR, factor) }

    fun getLyricsGlowFactor(): Float = getSafeFloat(KEY_LYRICS_GLOW_FACTOR, 1.0f)
    fun setLyricsGlowFactor(factor: Float) = prefs.edit { putFloat(KEY_LYRICS_GLOW_FACTOR, factor) }

    fun getLyricsFillTransitionWidth(): Float = getSafeFloat(KEY_LYRICS_FILL_TRANSITION_WIDTH, 8.0f)
    fun setLyricsFillTransitionWidth(width: Float) = prefs.edit { putFloat(KEY_LYRICS_FILL_TRANSITION_WIDTH, width) }

    fun getLyricsLineSpacing(): Float = getSafeFloat(KEY_LYRICS_LINE_SPACING, 1.3f)
    fun setLyricsLineSpacing(spacing: Float) = prefs.edit { putFloat(KEY_LYRICS_LINE_SPACING, spacing) }

    fun getLyricsFont(): LyricsFont {
        val name = prefs.getString(KEY_LYRICS_FONT, LyricsFont.APPLE.name)
        return try {
            LyricsFont.valueOf(name ?: LyricsFont.APPLE.name)
        } catch (_: Exception) {
            LyricsFont.APPLE
        }
    }
    fun setLyricsFont(font: LyricsFont) = prefs.edit { putString(KEY_LYRICS_FONT, font.name) }

    fun getLyricsDuetViewEnabled(): Boolean = prefs.getBoolean(KEY_LYRICS_DUET_VIEW, true)
    fun setLyricsDuetViewEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LYRICS_DUET_VIEW, enabled) }

    fun getLocalMediaEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_MEDIA_ENABLED, false)
    fun setLocalMediaEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_LOCAL_MEDIA_ENABLED, enabled) }
    fun getLocalMediaUris(): Set<String> = prefs.getStringSet(KEY_LOCAL_MEDIA_URIS_SET, emptySet()) ?: emptySet()
    fun addLocalMediaUri(uri: String) {
        val c = getLocalMediaUris().toMutableSet(); c.add(uri); prefs.edit { putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c) }
    }

    fun removeLocalMediaUri(uri: String) {
        val c = getLocalMediaUris().toMutableSet(); c.remove(uri); prefs.edit {
            putStringSet(
                KEY_LOCAL_MEDIA_URIS_SET,
                c
            )
        }
    }

    fun getStartDestination(): StartDestination {
        val n = prefs.getString(KEY_START_DESTINATION, StartDestination.HOME.name); return try {
            StartDestination.valueOf(n!!)
        } catch (_: Exception) {
            StartDestination.HOME
        }
    }

    fun setStartDestination(dest: StartDestination) = prefs.edit { putString(KEY_START_DESTINATION, dest.name) }
    fun getDynamicTheme(): Boolean = prefs.getBoolean(KEY_DYNAMIC_THEME, true)
    fun setDynamicTheme(enabled: Boolean) = prefs.edit { putBoolean(KEY_DYNAMIC_THEME, enabled) }
    fun getTrackDynamicTheme(): Boolean = prefs.getBoolean(KEY_TRACK_DYNAMIC_THEME, false)
    fun setTrackDynamicTheme(enabled: Boolean) = prefs.edit { putBoolean(KEY_TRACK_DYNAMIC_THEME, enabled) }
    fun getThemeMode(): AppThemeMode {
        val n = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name); return try {
            AppThemeMode.valueOf(n!!)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) = prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    fun getPureBlack(): Boolean = prefs.getBoolean(KEY_PURE_BLACK, false)
    fun setPureBlack(enabled: Boolean) = prefs.edit { putBoolean(KEY_PURE_BLACK, enabled) }
    fun getPlayerStyle(): PlayerBackgroundStyle {
        val n = prefs.getString(KEY_PLAYER_STYLE, PlayerBackgroundStyle.APPLE_MUSIC.name); return try {
            PlayerBackgroundStyle.valueOf(n!!)
        } catch (_: Exception) {
            PlayerBackgroundStyle.APPLE_MUSIC
        }
    }

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

    fun setBottomMenuItems(items: List<String>) =
        prefs.edit { putString(KEY_BOTTOM_MENU_ITEMS, items.joinToString(",")) }

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

    fun getPlayerDesign(): PlayerDesign {
        val raw = prefs.getString(KEY_PLAYER_DESIGN, null)
        if (raw != null) {
            try {
                return PlayerDesign.valueOf(raw)
            } catch (_: Exception) {}
        }
        if (getPlayerProgressMode() == PlayerProgressMode.SOUNDCLOUD) {
            return PlayerDesign.SOUNDCLOUD
        }
        return if (getNewPlayerDesignEnabled()) PlayerDesign.MODERN else PlayerDesign.CLASSIC
    }

    fun setPlayerDesign(design: PlayerDesign) {
        prefs.edit {
            putString(KEY_PLAYER_DESIGN, design.name)
            putBoolean(KEY_NEW_PLAYER_DESIGN, design != PlayerDesign.CLASSIC)
            if (design == PlayerDesign.SOUNDCLOUD) {
                putString(KEY_PLAYER_PROGRESS_MODE, PlayerProgressMode.SOUNDCLOUD.name)
                putBoolean(KEY_WAVEFORM_COMMENTS, true)
            } else if (design == PlayerDesign.MODERN) {
                if (getPlayerProgressMode() == PlayerProgressMode.SOUNDCLOUD || !prefs.contains(KEY_PLAYER_PROGRESS_MODE)) {
                    putString(KEY_PLAYER_PROGRESS_MODE, PlayerProgressMode.CLASSIC_BAR.name)
                }
            }
        }
    }

    fun getPlayerDesignFlow(): Flow<PlayerDesign> = callbackFlow {
        trySend(getPlayerDesign())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_PLAYER_DESIGN || key == KEY_NEW_PLAYER_DESIGN) {
                trySend(getPlayerDesign())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getWaveformCommentsEnabled(): Boolean = prefs.getBoolean(KEY_WAVEFORM_COMMENTS, false)
    fun setWaveformCommentsEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_WAVEFORM_COMMENTS, enabled)
            putString(
                KEY_PLAYER_PROGRESS_MODE,
                if (enabled) PlayerProgressMode.SOUNDCLOUD.name else PlayerProgressMode.CLASSIC_BAR.name
            )
        }
    }

    fun getPlayerProgressMode(): PlayerProgressMode {
        val raw = prefs.getString(KEY_PLAYER_PROGRESS_MODE, null)
        return if (raw != null) {
            try {
                PlayerProgressMode.valueOf(raw)
            } catch (e: Exception) {
                PlayerProgressMode.CLASSIC_BAR
            }
        } else {
            PlayerProgressMode.CLASSIC_BAR
        }
    }

    fun setPlayerProgressMode(mode: PlayerProgressMode) {
        prefs.edit {
            putString(KEY_PLAYER_PROGRESS_MODE, mode.name)
            putBoolean(KEY_WAVEFORM_COMMENTS, mode != PlayerProgressMode.CLASSIC_BAR)
        }
    }

    fun getWaveformCommentsPopupEnabled(): Boolean = prefs.getBoolean(KEY_WAVEFORM_COMMENTS_POPUP, true)
    fun setWaveformCommentsPopupEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_WAVEFORM_COMMENTS_POPUP, enabled) }

    fun getSoundCloudReactionsBarEnabled(): Boolean = prefs.getBoolean(KEY_SOUNDCLOUD_REACTIONS_BAR, true)
    fun setSoundCloudReactionsBarEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_SOUNDCLOUD_REACTIONS_BAR, enabled) }

    fun getSoundCloudParallaxEnabled(): Boolean = prefs.getBoolean(KEY_SOUNDCLOUD_PARALLAX, true)
    fun setSoundCloudParallaxEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SOUNDCLOUD_PARALLAX, enabled) }

    fun getPlayerSliderStyle(): PlayerSliderStyle {
        val raw = prefs.getString(KEY_PLAYER_SLIDER_STYLE, null)
        return if (raw != null) {
            try {
                PlayerSliderStyle.valueOf(raw)
            } catch (e: Exception) {
                PlayerSliderStyle.BAR
            }
        } else {
            if (getPlayerDesign() == PlayerDesign.PIXEL_PLAYER) {
                PlayerSliderStyle.WAVY
            } else {
                PlayerSliderStyle.BAR
            }
        }
    }

    fun setPlayerSliderStyle(style: PlayerSliderStyle) {
        prefs.edit { putString(KEY_PLAYER_SLIDER_STYLE, style.name) }
    }

    fun getSoundCloudSlot(index: Int): PlayerActionButtonSlot {
        val defaultSlot = when (index) {
            0 -> PlayerActionButtonSlot.LIKE
            1 -> PlayerActionButtonSlot.COMMENTS
            2 -> PlayerActionButtonSlot.SHARE
            3 -> PlayerActionButtonSlot.QUEUE
            4 -> PlayerActionButtonSlot.MORE
            else -> PlayerActionButtonSlot.NONE
        }
        val raw = prefs.getString("${KEY_SOUNDCLOUD_SLOT_PREFIX}$index", null) ?: return defaultSlot
        return try {
            PlayerActionButtonSlot.valueOf(raw)
        } catch (e: Exception) {
            defaultSlot
        }
    }

    fun setSoundCloudSlot(index: Int, slot: PlayerActionButtonSlot) {
        prefs.edit { putString("${KEY_SOUNDCLOUD_SLOT_PREFIX}$index", slot.name) }
    }

    fun getClassicSlot(index: Int): PlayerActionButtonSlot {
        val defaultSlot = when (index) {
            0 -> PlayerActionButtonSlot.AUDIO_FX
            1 -> PlayerActionButtonSlot.SHUFFLE
            2 -> PlayerActionButtonSlot.REPEAT
            3 -> PlayerActionButtonSlot.QUEUE
            else -> PlayerActionButtonSlot.NONE
        }
        val raw = prefs.getString("${KEY_CLASSIC_SLOT_PREFIX}$index", null) ?: return defaultSlot
        return try {
            PlayerActionButtonSlot.valueOf(raw)
        } catch (e: Exception) {
            defaultSlot
        }
    }

    fun setClassicSlot(index: Int, slot: PlayerActionButtonSlot) {
        prefs.edit { putString("${KEY_CLASSIC_SLOT_PREFIX}$index", slot.name) }
    }

    fun getPixelSlot(index: Int): PlayerActionButtonSlot {
        val defaultSlot = when (index) {
            0 -> PlayerActionButtonSlot.SHUFFLE
            1 -> PlayerActionButtonSlot.REPEAT
            2 -> PlayerActionButtonSlot.LIKE
            3 -> PlayerActionButtonSlot.QUEUE
            else -> PlayerActionButtonSlot.NONE
        }
        val raw = prefs.getString("${KEY_PIXEL_SLOT_PREFIX}$index", null) ?: return defaultSlot
        return try {
            PlayerActionButtonSlot.valueOf(raw)
        } catch (e: Exception) {
            defaultSlot
        }
    }

    fun setPixelSlot(index: Int, slot: PlayerActionButtonSlot) {
        prefs.edit { putString("${KEY_PIXEL_SLOT_PREFIX}$index", slot.name) }
    }

    fun getSlotForDesign(design: PlayerDesign, index: Int): PlayerActionButtonSlot {
        return when (design) {
            PlayerDesign.PIXEL_PLAYER -> getPixelSlot(index)
            PlayerDesign.SOUNDCLOUD -> getSoundCloudSlot(index)
            PlayerDesign.MODERN, PlayerDesign.CLASSIC -> getClassicSlot(index)
        }
    }

    fun setSlotForDesign(design: PlayerDesign, index: Int, slot: PlayerActionButtonSlot) {
        when (design) {
            PlayerDesign.PIXEL_PLAYER -> setPixelSlot(index, slot)
            PlayerDesign.SOUNDCLOUD -> setSoundCloudSlot(index, slot)
            PlayerDesign.MODERN, PlayerDesign.CLASSIC -> setClassicSlot(index, slot)
        }
    }

    fun resetDesignCustomization(design: PlayerDesign) {
        prefs.edit {
            when (design) {
                PlayerDesign.PIXEL_PLAYER -> {
                    remove(KEY_PLAYER_SLIDER_STYLE)
                    for (i in 0..3) {
                        remove("${KEY_PIXEL_SLOT_PREFIX}$i")
                    }
                }
                PlayerDesign.SOUNDCLOUD -> {
                    remove(KEY_WAVEFORM_COMMENTS_POPUP)
                    remove(KEY_SOUNDCLOUD_REACTIONS_BAR)
                    remove(KEY_SOUNDCLOUD_PARALLAX)
                    for (i in 0..4) {
                        remove("${KEY_SOUNDCLOUD_SLOT_PREFIX}$i")
                    }
                }
                PlayerDesign.MODERN, PlayerDesign.CLASSIC -> {
                    remove(KEY_PLAYER_SLIDER_STYLE)
                    remove(KEY_PLAYER_PROGRESS_MODE)
                    remove(KEY_WAVEFORM_COMMENTS_POPUP)
                    for (i in 0..3) {
                        remove("${KEY_CLASSIC_SLOT_PREFIX}$i")
                    }
                }
            }
        }
    }

    fun getSlot(mode: PlayerProgressMode, index: Int): PlayerActionButtonSlot {
        return if (mode == PlayerProgressMode.SOUNDCLOUD) {
            getSoundCloudSlot(index)
        } else {
            getClassicSlot(index)
        }
    }

    fun setSlot(mode: PlayerProgressMode, index: Int, slot: PlayerActionButtonSlot) {
        if (mode == PlayerProgressMode.SOUNDCLOUD) {
            setSoundCloudSlot(index, slot)
        } else {
            setClassicSlot(index, slot)
        }
    }

    fun resetSoundCloudCustomization() {
        prefs.edit {
            remove(KEY_WAVEFORM_COMMENTS_POPUP)
            remove(KEY_SOUNDCLOUD_REACTIONS_BAR)
            remove(KEY_SOUNDCLOUD_PARALLAX)
            for (i in 0..4) {
                remove("${KEY_SOUNDCLOUD_SLOT_PREFIX}$i")
            }
            for (i in 0..3) {
                remove("${KEY_CLASSIC_SLOT_PREFIX}$i")
            }
            for (i in 0..3) {
                remove("${KEY_PIXEL_SLOT_PREFIX}$i")
            }
        }
    }

    fun bottomMenuBlurFlow(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BOTTOM_MENU_BLUR) trySend(getBottomMenuBlurEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getBottomMenuBlurEnabled())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getSavePositionEnabled(): Boolean = prefs.getBoolean(KEY_SAVE_POSITION, true)
    fun setSavePositionEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_SAVE_POSITION, enabled) }

    fun savePosition(position: Long) {
        if (getSavePositionEnabled()) {
            prefs.edit { putLong(KEY_POSITION, position) }
        }
    }

    fun savePlaybackState(
        track: Track?,
        position: Long,
        queue: List<Track>,
        context: PlaybackContext?,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode
    ) {
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
            if (getSavePositionEnabled()) putLong(KEY_POSITION, position) else remove(KEY_POSITION)
            putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
            putString(KEY_REPEAT_MODE, repeatMode.name)
        }
    }

    fun saveEffects(state: AudioEffectsState) {
        prefs.edit { putString(KEY_EFFECTS, gson.toJson(state)) }
    }

    fun saveDownloadLocation(uriString: String?) {
        if (uriString != null) prefs.edit { putString(KEY_DOWNLOAD_DIR, uriString) } else prefs.edit {
            remove(
                KEY_DOWNLOAD_DIR
            )
        }
    }

    fun getDownloadLocation(): String? = prefs.getString(KEY_DOWNLOAD_DIR, null)
    fun getLastTrack(): Track? {
        if (!getPersistentQueueEnabled()) return null;
        val json = prefs.getString(KEY_TRACK_JSON, null) ?: return null; return try {
            gson.fromJson(json, Track::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun getLastPosition(): Long = if (getSavePositionEnabled()) prefs.getLong(KEY_POSITION, 0L) else 0L
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
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLastContext(): PlaybackContext? {
        if (!getPersistentQueueEnabled()) return null;
        val json = prefs.getString(KEY_CONTEXT_JSON, null) ?: return null; return try {
            gson.fromJson(json, PlaybackContext::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun getLastShuffleEnabled(): Boolean = prefs.getBoolean(KEY_SHUFFLE_MODE, false)
    fun getLastRepeatMode(): RepeatMode {
        val modeName = prefs.getString(KEY_REPEAT_MODE, RepeatMode.NONE.name); return try {
            RepeatMode.valueOf(modeName ?: RepeatMode.NONE.name)
        } catch (_: Exception) {
            RepeatMode.NONE
        }
    }

    fun getLastEffects(): AudioEffectsState {
        val json = prefs.getString(KEY_EFFECTS, null) ?: return AudioEffectsState(); return try {
            gson.fromJson(json, AudioEffectsState::class.java)
        } catch (_: Exception) {
            AudioEffectsState()
        }
    }

    fun getPinnedAudioFx(): List<String> {
        val json = prefs.getString(KEY_PINNED_AUDIO_FX, null) ?: return DEFAULT_PINNED_AUDIO_FX
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String>? = gson.fromJson(json, type)
            if (list.isNullOrEmpty()) DEFAULT_PINNED_AUDIO_FX else list
        } catch (_: Exception) {
            DEFAULT_PINNED_AUDIO_FX
        }
    }

    fun setPinnedAudioFx(fxIds: List<String>) {
        prefs.edit { putString(KEY_PINNED_AUDIO_FX, gson.toJson(fxIds)) }
    }

    fun getSoundCloudHistorySyncEnabled(): Boolean = prefs.getBoolean(KEY_SOUNDCLOUD_HISTORY_SYNC, true)

    fun setSoundCloudHistorySyncEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SOUNDCLOUD_HISTORY_SYNC, enabled) }
    }

    fun getProxyEnabled(): Boolean = prefs.getBoolean(KEY_PROXY_ENABLED, false)

    fun setProxyEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PROXY_ENABLED, enabled) }
    }

    fun getProxyType(): String = prefs.getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP"

    fun setProxyType(type: String) {
        prefs.edit { putString(KEY_PROXY_TYPE, type) }
    }

    fun getProxyHost(): String = prefs.getString(KEY_PROXY_HOST, "") ?: ""

    fun setProxyHost(host: String) {
        prefs.edit { putString(KEY_PROXY_HOST, host.trim()) }
    }

    fun getProxyPort(): Int = prefs.getInt(KEY_PROXY_PORT, 8080)

    fun setProxyPort(port: Int) {
        prefs.edit { putInt(KEY_PROXY_PORT, port) }
    }

    fun getProxyAuthEnabled(): Boolean = prefs.getBoolean(KEY_PROXY_AUTH_ENABLED, false)

    fun setProxyAuthEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PROXY_AUTH_ENABLED, enabled) }
    }

    fun getProxyUsername(): String = prefs.getString(KEY_PROXY_USERNAME, "") ?: ""

    fun setProxyUsername(username: String) {
        prefs.edit { putString(KEY_PROXY_USERNAME, username.trim()) }
    }

    fun getProxyPassword(): String = prefs.getString(KEY_PROXY_PASSWORD, "") ?: ""

    fun setProxyPassword(password: String) {
        prefs.edit { putString(KEY_PROXY_PASSWORD, password) }
    }

    // Proxy Profiles (Multi-proxy list)
    fun getSavedProxyProfiles(): List<com.alananasss.kittytune.data.network.ProxyProfile> {
        val json = prefs.getString(KEY_PROXY_PROFILES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<com.alananasss.kittytune.data.network.ProxyProfile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProxyProfiles(profiles: List<com.alananasss.kittytune.data.network.ProxyProfile>) {
        prefs.edit { putString(KEY_PROXY_PROFILES, gson.toJson(profiles)) }
    }

    fun addOrUpdateProxyProfile(profile: com.alananasss.kittytune.data.network.ProxyProfile) {
        val list = getSavedProxyProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            list[index] = profile
        } else {
            list.add(profile)
        }
        saveProxyProfiles(list)
    }

    fun deleteProxyProfile(profileId: String) {
        val list = getSavedProxyProfiles().filterNot { it.id == profileId }
        saveProxyProfiles(list)
        if (getSelectedProxyProfileId() == profileId) {
            setSelectedProxyProfileId(null)
        }
    }

    fun getLikedSpotifyArtists(): Set<String> {
        return prefs.getStringSet("liked_spotify_artists", emptySet()) ?: emptySet()
    }

    fun isSpotifyArtistLiked(artistId: String): Boolean {
        return getLikedSpotifyArtists().contains(artistId)
    }

    fun toggleLikeSpotifyArtist(artistId: String): Boolean {
        val current = getLikedSpotifyArtists().toMutableSet()
        val isNowLiked = if (current.contains(artistId)) {
            current.remove(artistId)
            false
        } else {
            current.add(artistId)
            true
        }
        prefs.edit { putStringSet("liked_spotify_artists", current) }
        return isNowLiked
    }

    fun saveSpotifyArtistMapping(numericId: Long, spotifyId: String) {
        prefs.edit { putString("spotify_artist_mapping_$numericId", spotifyId) }
    }

    fun getSpotifyArtistIdForStableId(numericId: Long): String? {
        return prefs.getString("spotify_artist_mapping_$numericId", null)
    }

    fun removeSpotifyArtistMapping(numericId: Long) {
        prefs.edit { remove("spotify_artist_mapping_$numericId") }
    }

    fun getSelectedProxyProfileId(): String? = prefs.getString(KEY_SELECTED_PROXY_PROFILE_ID, null)

    fun setSelectedProxyProfileId(id: String?) {
        prefs.edit {
            if (id != null) putString(KEY_SELECTED_PROXY_PROFILE_ID, id)
            else remove(KEY_SELECTED_PROXY_PROFILE_ID)
        }
    }

    fun getTrackRemovalMethod(): TrackRemovalMethod {
        val raw = prefs.getString(KEY_TRACK_REMOVAL_METHOD, TrackRemovalMethod.SWIPE_AND_MENU.name)
        return try {
            TrackRemovalMethod.valueOf(raw ?: TrackRemovalMethod.SWIPE_AND_MENU.name)
        } catch (_: Exception) {
            TrackRemovalMethod.SWIPE_AND_MENU
        }
    }

    fun setTrackRemovalMethod(method: TrackRemovalMethod) {
        prefs.edit { putString(KEY_TRACK_REMOVAL_METHOD, method.name) }
    }

    fun setCachedUserId(id: Long) {
        prefs.edit { putLong(KEY_CACHED_USER_ID, id) }
    }

    fun getCachedUserId(): Long {
        return prefs.getLong(KEY_CACHED_USER_ID, 0L)
    }

    fun setCachedUsername(username: String?) {
        prefs.edit {
            if (username != null) putString(KEY_CACHED_USERNAME, username)
            else remove(KEY_CACHED_USERNAME)
        }
    }

    fun getCachedUsername(): String? {
        return prefs.getString(KEY_CACHED_USERNAME, null)
    }

    // Audio Provider Order
    fun getAudioProviderOrder(): List<com.alananasss.kittytune.audio.providers.AudioProviderOrderItem> {
        val raw = prefs.getString(KEY_AUDIO_PROVIDER_ORDER, null)
        return com.alananasss.kittytune.audio.providers.AudioProviderOrder.deserialize(raw)
    }

    fun setAudioProviderOrder(order: List<com.alananasss.kittytune.audio.providers.AudioProviderOrderItem>) {
        prefs.edit { putString(KEY_AUDIO_PROVIDER_ORDER, com.alananasss.kittytune.audio.providers.AudioProviderOrder.serialize(order)) }
    }

    // Qobuz
    fun getQobuzCountry(): String = prefs.getString(KEY_QOBUZ_COUNTRY, "US") ?: "US"
    fun setQobuzCountry(country: String) = prefs.edit { putString(KEY_QOBUZ_COUNTRY, country.trim().uppercase(java.util.Locale.US)) }

    fun getQobuzCustomInstances(): String = prefs.getString(KEY_QOBUZ_CUSTOM_INSTANCES, com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.DEFAULT_INSTANCE) ?: com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.DEFAULT_INSTANCE
    fun setQobuzCustomInstances(instances: String) = prefs.edit { putString(KEY_QOBUZ_CUSTOM_INSTANCES, instances.trim()) }

    fun getQobuzQuality(): Int = prefs.getInt(KEY_QOBUZ_QUALITY, 27)
    fun setQobuzQuality(quality: Int) = prefs.edit { putInt(KEY_QOBUZ_QUALITY, quality) }

    // Tidal
    fun getTidalResolverEndpoints(): String = prefs.getString(KEY_TIDAL_RESOLVER_ENDPOINTS, "") ?: ""
    fun setTidalResolverEndpoints(endpoints: String) = prefs.edit { putString(KEY_TIDAL_RESOLVER_ENDPOINTS, endpoints.trim()) }

    fun getTidalAudioQuality(): com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality {
        val raw = prefs.getString(KEY_TIDAL_AUDIO_QUALITY, com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320.name)
        return try {
            com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.valueOf(raw ?: com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320.name)
        } catch (_: Exception) {
            com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320
        }
    }
    fun setTidalAudioQuality(quality: com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality) =
        prefs.edit { putString(KEY_TIDAL_AUDIO_QUALITY, quality.name) }

    fun getTidalCookie(): String = prefs.getString(KEY_TIDAL_COOKIE, "") ?: ""
    fun setTidalCookie(cookie: String) = prefs.edit { putString(KEY_TIDAL_COOKIE, cookie.trim()) }

    // Deezer
    fun getDeezerResolverUrl(): String = prefs.getString(KEY_DEEZER_RESOLVER_URL, com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider.DEFAULT_RESOLVER_URL) ?: com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider.DEFAULT_RESOLVER_URL
    fun setDeezerResolverUrl(url: String) = prefs.edit { putString(KEY_DEEZER_RESOLVER_URL, url.trim()) }

    fun getDeezerAudioQuality(): com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality {
        val raw = prefs.getString(KEY_DEEZER_AUDIO_QUALITY, com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128.name)
        return try {
            com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.valueOf(raw ?: com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128.name)
        } catch (_: Exception) {
            com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128
        }
    }
    fun setDeezerAudioQuality(quality: com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality) =
        prefs.edit { putString(KEY_DEEZER_AUDIO_QUALITY, quality.name) }

    fun getDeezerFastMode(): Boolean = prefs.getBoolean(KEY_DEEZER_FAST_MODE, false)
    fun setDeezerFastMode(fastMode: Boolean) = prefs.edit { putBoolean(KEY_DEEZER_FAST_MODE, fastMode) }

    fun getDeezerProxyMode(): com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode {
        val raw = prefs.getString(KEY_DEEZER_PROXY_MODE, com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.DIRECT.name)
        return try {
            com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.valueOf(raw ?: com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.DIRECT.name)
        } catch (_: Exception) {
            com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.DIRECT
        }
    }
    fun setDeezerProxyMode(mode: com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode) =
        prefs.edit { putString(KEY_DEEZER_PROXY_MODE, mode.name) }

    fun getDeezerProxyUrl(): String = prefs.getString(KEY_DEEZER_PROXY_URL, "") ?: ""
    fun setDeezerProxyUrl(url: String) = prefs.edit { putString(KEY_DEEZER_PROXY_URL, url.trim()) }

    fun getDeezerCookie(): String = prefs.getString(KEY_DEEZER_COOKIE, "") ?: ""
    fun setDeezerCookie(cookie: String) = prefs.edit { putString(KEY_DEEZER_COOKIE, cookie.trim()) }

    fun getDeezerUseAccount(): Boolean = prefs.getBoolean(KEY_DEEZER_USE_ACCOUNT, true)
    fun setDeezerUseAccount(useAccount: Boolean) = prefs.edit { putBoolean(KEY_DEEZER_USE_ACCOUNT, useAccount) }

    fun isSetupCompleted(): Boolean = prefs.getBoolean("is_setup_completed", false)
    fun setSetupCompleted(completed: Boolean) = prefs.edit { putBoolean("is_setup_completed", completed) }

    fun isSupportBannerDismissed(): Boolean = prefs.getBoolean("support_banner_dismissed", false)
    fun setSupportBannerDismissed(dismissed: Boolean) = prefs.edit { putBoolean("support_banner_dismissed", dismissed) }

    fun getTracksPlayedCount(): Int = prefs.getInt("tracks_played_count", 0)
    fun incrementTracksPlayedCount() {
        prefs.edit { putInt("tracks_played_count", getTracksPlayedCount() + 1) }
    }

    fun getSupportBannerRemindLaterUntil(): Long = prefs.getLong("support_banner_remind_later_until", 0L)
    fun snoozeSupportBanner(days: Int = 3) {
        val until = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
        prefs.edit { putLong("support_banner_remind_later_until", until) }
    }

    fun shouldShowSupportBanner(historyTrackCount: Int = 0): Boolean {
        if (isSupportBannerDismissed()) return false

        // Check if snoozed via "Remind me later"
        if (System.currentTimeMillis() < getSupportBannerRemindLaterUntil()) return false

        // Check install / first launch time
        val firstInstallTime = try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (_: Exception) {
            0L
        }
        val firstLaunchTime = prefs.getLong("app_first_launch_time", 0L).let { stored ->
            if (stored == 0L) {
                val initTime = if (firstInstallTime > 0L) firstInstallTime else System.currentTimeMillis()
                prefs.edit { putLong("app_first_launch_time", initTime) }
                initTime
            } else stored
        }

        val twoDaysMillis = 2 * 24 * 60 * 60 * 1000L
        val isUsedForTwoDays = (System.currentTimeMillis() - firstLaunchTime) >= twoDaysMillis

        val totalTracks = maxOf(historyTrackCount, getTracksPlayedCount())
        val hasPlayedEnoughTracks = totalTracks >= 5

        // Condition: at least 5-10 tracks played OR at least 2 days of usage (with at least 1 track played)
        return hasPlayedEnoughTracks || (isUsedForTwoDays && totalTracks >= 1)
    }
}
