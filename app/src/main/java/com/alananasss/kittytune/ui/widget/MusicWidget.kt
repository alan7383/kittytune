    package com.alananasss.kittytune.ui.widget

    import android.content.Context
    import android.graphics.Bitmap
    import android.graphics.BitmapFactory
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.datastore.preferences.core.Preferences
    import androidx.datastore.preferences.core.booleanPreferencesKey
    import androidx.datastore.preferences.core.stringPreferencesKey
    import androidx.glance.ColorFilter
    import androidx.glance.GlanceId
    import androidx.glance.GlanceModifier
    import androidx.glance.GlanceTheme
    import androidx.glance.Image
    import androidx.glance.ImageProvider
    import androidx.glance.LocalContext
    import androidx.glance.action.actionParametersOf
    import androidx.glance.action.clickable
    import androidx.glance.appwidget.GlanceAppWidget
    import androidx.glance.appwidget.GlanceAppWidgetManager
    import androidx.glance.appwidget.action.actionRunCallback
    import androidx.glance.appwidget.cornerRadius
    import androidx.glance.appwidget.provideContent
    import androidx.glance.appwidget.state.updateAppWidgetState
    import androidx.glance.background
    import androidx.glance.currentState
    import androidx.glance.layout.Alignment
    import androidx.glance.layout.Box
    import androidx.glance.layout.Column
    import androidx.glance.layout.ContentScale
    import androidx.glance.layout.Row
    import androidx.glance.layout.Spacer
    import androidx.glance.layout.fillMaxHeight
    import androidx.glance.layout.fillMaxSize
    import androidx.glance.layout.fillMaxWidth
    import androidx.glance.layout.height
    import androidx.glance.layout.padding
    import androidx.glance.layout.size
    import androidx.glance.layout.width
    import androidx.glance.text.FontWeight
    import androidx.glance.text.Text
    import androidx.glance.text.TextStyle
    import androidx.glance.unit.ColorProvider
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.LikeRepository
    import com.alananasss.kittytune.data.MusicManager
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import kotlinx.coroutines.MainScope
    import kotlinx.coroutines.launch
    import java.io.File

    // simple data class to type speeds without error
    data class WidgetSpeedOption(val label: String, val value: Float)

    class MusicWidget : GlanceAppWidget() {

        companion object {
            val KEY_TITLE = stringPreferencesKey("title")
            val KEY_ARTIST = stringPreferencesKey("artist")
            val KEY_COVER_PATH = stringPreferencesKey("cover_path")
            val KEY_IS_PLAYING = booleanPreferencesKey("is_playing")
            val KEY_IS_LIKED = booleanPreferencesKey("is_liked")

            // configuration keys
            val KEY_SHOW_BASS = booleanPreferencesKey("show_bass")
            val KEY_SHOW_8D = booleanPreferencesKey("show_8d")
            val KEY_SHOW_MUFFLED = booleanPreferencesKey("show_muffled")
            val KEY_SHOW_REVERB = booleanPreferencesKey("show_reverb")
            val KEY_SHOW_PITCH = booleanPreferencesKey("show_pitch")
            val KEY_CUSTOM_SPEEDS = stringPreferencesKey("custom_speeds_str")

            // state keys
            val KEY_CURRENT_SPEED = stringPreferencesKey("curr_speed")
            val KEY_BASS_ACTIVE = booleanPreferencesKey("bass_active")
            val KEY_8D_ACTIVE = booleanPreferencesKey("8d_active")
            val KEY_MUFFLED_ACTIVE = booleanPreferencesKey("muffled_active")
            val KEY_REVERB_ACTIVE = booleanPreferencesKey("reverb_active")
            val KEY_PITCH_ACTIVE = booleanPreferencesKey("pitch_active")

            fun update(context: Context) {
                MainScope().launch {
                    val track = MusicManager.currentTrack
                    val isPlaying = try { MusicManager.player.isPlaying } catch(e: Exception) { false }
                    val isLiked = if (track != null) LikeRepository.isTrackLiked(track.id) else false

                    val prefsRepo = PlayerPreferences(context)
                    val effects = prefsRepo.getLastEffects()

                    val manager = GlanceAppWidgetManager(context)

                    // update main widgets
                    val mainWidget = MusicWidget()
                    val mainIds = manager.getGlanceIds(MusicWidget::class.java)
                    mainIds.forEach { glanceId ->
                        updateAppWidgetState(context, glanceId) { prefs ->
                            updateCommonState(context, prefs, track, isPlaying, isLiked)

                            prefs[KEY_CURRENT_SPEED] = effects.speed.toString()
                            prefs[KEY_BASS_ACTIVE] = effects.isBassBoostEnabled
                            prefs[KEY_8D_ACTIVE] = effects.is8DEnabled
                            prefs[KEY_MUFFLED_ACTIVE] = effects.isMuffledEnabled
                            prefs[KEY_REVERB_ACTIVE] = effects.isReverbEnabled
                            prefs[KEY_PITCH_ACTIVE] = effects.isPitchEnabled
                        }
                        mainWidget.update(context, glanceId)
                    }

                    // update mini widgets
                    val miniWidget = MiniMusicWidget()
                    val miniIds = manager.getGlanceIds(MiniMusicWidget::class.java)
                    miniIds.forEach { glanceId ->
                        updateAppWidgetState(context, glanceId) { prefs ->
                            updateCommonState(context, prefs, track, isPlaying, isLiked)
                        }
                        miniWidget.update(context, glanceId)
                    }
                }
            }

            private fun getCachedArtworkPath(context: Context, trackId: Long): String? {
                val artFile = File(context.filesDir, "art_${trackId}.jpg")
                if (artFile.exists()) return artFile.absolutePath
                return null
            }

            private fun updateCommonState(
                context: Context,
                prefs: androidx.datastore.preferences.core.MutablePreferences,
                track: com.alananasss.kittytune.domain.Track?,
                isPlaying: Boolean,
                isLiked: Boolean
            ) {
                if (track != null) {
                    prefs[KEY_TITLE] = track.title ?: context.getString(R.string.widget_unknown)
                    prefs[KEY_ARTIST] = track.user?.username ?: context.getString(R.string.widget_unknown)
                    val path = getCachedArtworkPath(context, track.id)
                    prefs[KEY_COVER_PATH] = path ?: ""
                } else {
                    prefs[KEY_TITLE] = context.getString(R.string.widget_default_title)
                    prefs[KEY_ARTIST] = context.getString(R.string.widget_default_artist)
                    prefs[KEY_COVER_PATH] = ""
                }
                prefs[KEY_IS_PLAYING] = isPlaying
                prefs[KEY_IS_LIKED] = isLiked
            }
        }

        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent {
                GlanceTheme {
                    WidgetContent()
                }
            }
        }

        @Composable
        fun WidgetContent() {
            val context = LocalContext.current
            val prefs = currentState<Preferences>()

            val title = prefs[KEY_TITLE] ?: context.getString(R.string.widget_default_title)
            val artist = prefs[KEY_ARTIST] ?: context.getString(R.string.widget_select_track)
            val coverPath = prefs[KEY_COVER_PATH] ?: ""
            val isPlaying = prefs[KEY_IS_PLAYING] ?: false
            val isLiked = prefs[KEY_IS_LIKED] ?: false

            val showBass = prefs[KEY_SHOW_BASS] ?: false
            val show8D = prefs[KEY_SHOW_8D] ?: false
            val showMuffled = prefs[KEY_SHOW_MUFFLED] ?: false
            val showReverb = prefs[KEY_SHOW_REVERB] ?: false
            val showPitch = prefs[KEY_SHOW_PITCH] ?: false

            val customSpeedsRaw = prefs[KEY_CUSTOM_SPEEDS] ?: ""

            // transforming string data into typed objects
            val customSpeedsList: List<WidgetSpeedOption> = if (customSpeedsRaw.isNotEmpty()) {
                customSpeedsRaw.split("|").mapNotNull {
                    val parts = it.split(":")
                    if(parts.size == 2) {
                        val label = parts[0]
                        val value = parts[1].toFloatOrNull()
                        if (value != null) WidgetSpeedOption(label, value) else null
                    } else null
                }
            } else emptyList()

            val currentSpeedVal = (prefs[KEY_CURRENT_SPEED] ?: "1.0").toFloatOrNull() ?: 1.0f
            val isBassActive = prefs[KEY_BASS_ACTIVE] ?: false
            val is8DActive = prefs[KEY_8D_ACTIVE] ?: false
            val isMuffledActive = prefs[KEY_MUFFLED_ACTIVE] ?: false
            val isReverbActive = prefs[KEY_REVERB_ACTIVE] ?: false
            val isPitchActive = prefs[KEY_PITCH_ACTIVE] ?: false

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(28.dp)
                    .clickable(actionRunCallback<OpenAppAction>())
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap = rememberBitmapFromPath(coverPath)
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(110.dp)
                            .cornerRadius(16.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                    ) {
                        if (bitmap != null) {
                            Image(
                                provider = ImageProvider(bitmap),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_notification),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                                    modifier = GlanceModifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(GlanceModifier.width(16.dp))

                    Column(
                        modifier = GlanceModifier.fillMaxHeight().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. metadata row
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = title,
                                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = artist,
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                                    maxLines = 1
                                )
                            }

                            val heartIcon = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            val tintProvider = if (isLiked) ColorProvider(Color(0xFFFF4081)) else GlanceTheme.colors.onSurfaceVariant

                            Image(
                                provider = ImageProvider(heartIcon),
                                contentDescription = context.getString(R.string.desc_like),
                                colorFilter = ColorFilter.tint(tintProvider),
                                modifier = GlanceModifier.size(24.dp).clickable(actionRunCallback<ToggleLikeAction>())
                            )
                        }

                        Spacer(GlanceModifier.defaultWeight())

                        // 2. playback controls
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.End
                        ) {
                            Image(provider = ImageProvider(R.drawable.ic_skip_previous), contentDescription = context.getString(R.string.desc_previous), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface), modifier = GlanceModifier.size(32.dp).clickable(actionRunCallback<SkipPrevAction>()).padding(4.dp))
                            Spacer(GlanceModifier.width(12.dp))
                            Box(
                                modifier = GlanceModifier.size(48.dp).background(GlanceTheme.colors.primaryContainer).cornerRadius(24.dp).clickable(actionRunCallback<PlayPauseAction>()),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                                val descId = if (isPlaying) R.string.desc_pause else R.string.desc_play
                                Image(provider = ImageProvider(iconId), contentDescription = context.getString(descId), colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer), modifier = GlanceModifier.size(28.dp))
                            }
                            Spacer(GlanceModifier.width(12.dp))
                            Image(provider = ImageProvider(R.drawable.ic_skip_next), contentDescription = context.getString(R.string.desc_next), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface), modifier = GlanceModifier.size(32.dp).clickable(actionRunCallback<SkipNextAction>()).padding(4.dp))
                        }

                        // 3. custom buttons row (replacing lazyrow with row + foreach)
                        if (showBass || show8D || showMuffled || showReverb || showPitch || customSpeedsList.isNotEmpty()) {
                            Spacer(GlanceModifier.height(8.dp))

                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                if (showBass) { PillButton(context.getString(R.string.effect_bass_boost).take(4), isBassActive, "BASS"); Spacer(GlanceModifier.width(4.dp)) }
                                if (show8D) { PillButton("8D", is8DActive, "8D"); Spacer(GlanceModifier.width(4.dp)) }
                                if (showMuffled) { PillButton(context.getString(R.string.effect_muffled).take(4), isMuffledActive, "MUFFLED"); Spacer(GlanceModifier.width(4.dp)) }
                                if (showReverb) { PillButton("Verb", isReverbActive, "REVERB"); Spacer(GlanceModifier.width(4.dp)) }
                                if (showPitch) { PillButton("Ptch", isPitchActive, "PITCH"); Spacer(GlanceModifier.width(4.dp)) }

                                // standard loop on list, limited to 3 items to avoid overflow
                                customSpeedsList.take(3).forEach { option ->
                                    val isActive = (option.value == currentSpeedVal)
                                    SpeedPillButton(option.label, isActive, option.value)
                                    Spacer(GlanceModifier.width(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun PillButton(text: String, isActive: Boolean, effectKey: String) {
            val bgColor = if(isActive) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
            val contentColor = if(isActive) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant

            Box(
                modifier = GlanceModifier
                    .background(bgColor)
                    .cornerRadius(8.dp)
                    .clickable(actionRunCallback<ToggleSpecificEffectAction>(
                        actionParametersOf(ActionEffectKey to effectKey)
                    ))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        @Composable
        fun SpeedPillButton(text: String, isActive: Boolean, speedValue: Float) {
            val bgColor = if(isActive) GlanceTheme.colors.tertiary else GlanceTheme.colors.surfaceVariant
            val contentColor = if(isActive) GlanceTheme.colors.onTertiary else GlanceTheme.colors.onSurfaceVariant

            Box(
                modifier = GlanceModifier
                    .background(bgColor)
                    .cornerRadius(8.dp)
                    .clickable(actionRunCallback<SetSpecificSpeedAction>(
                        actionParametersOf(ActionSpeedKey to speedValue)
                    ))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        @Composable
        private fun rememberBitmapFromPath(path: String): Bitmap? {
            if (path.isEmpty()) return null
            return try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
        }
    }

