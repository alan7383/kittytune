    package com.alananasss.kittytune.ui.widget
    
    import android.content.Context
    import android.graphics.Bitmap
    import android.graphics.BitmapFactory
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.datastore.preferences.core.Preferences
    import androidx.glance.ColorFilter
    import androidx.glance.GlanceId
    import androidx.glance.GlanceModifier
    import androidx.glance.GlanceTheme
    import androidx.glance.Image
    import androidx.glance.ImageProvider
    import androidx.glance.LocalContext
    import androidx.glance.action.clickable
    import androidx.glance.appwidget.GlanceAppWidget
    import androidx.glance.appwidget.action.actionRunCallback
    import androidx.glance.appwidget.cornerRadius
    import androidx.glance.appwidget.provideContent
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
    import androidx.glance.layout.padding
    import androidx.glance.layout.size
    import androidx.glance.layout.width
    import androidx.glance.text.FontWeight
    import androidx.glance.text.Text
    import androidx.glance.text.TextStyle
    import com.alananasss.kittytune.R
    
    class MiniMusicWidget : GlanceAppWidget() {
    
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent {
                GlanceTheme {
                    MiniPillContent()
                }
            }
        }
    
        @Composable
        fun MiniPillContent() {
            val context = LocalContext.current
            val prefs = currentState<Preferences>()
    
            // retrieving data
            val title = prefs[MusicWidget.KEY_TITLE] ?: context.getString(R.string.widget_default_title)
            val artist = prefs[MusicWidget.KEY_ARTIST] ?: context.getString(R.string.widget_default_artist)
            val coverPath = prefs[MusicWidget.KEY_COVER_PATH] ?: ""
            val isPlaying = prefs[MusicWidget.KEY_IS_PLAYING] ?: false
    
            // container: pill shape with surface container color
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(32.dp) // fully rounded pill shape
                    .clickable(actionRunCallback<OpenAppAction>())
                    .padding(8.dp), // internal padding
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. album art (circle or rounded square on the left)
                    val bitmap = rememberBitmapFromPath(coverPath)
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(56.dp) // keep aspect ratio
                            .cornerRadius(28.dp) // circle shape for the art
                            .background(GlanceTheme.colors.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                provider = ImageProvider(bitmap),
                                contentDescription = context.getString(R.string.desc_artwork),
                                contentScale = ContentScale.Crop,
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                provider = ImageProvider(R.drawable.ic_notification),
                                contentDescription = context.getString(R.string.desc_artwork),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                                modifier = GlanceModifier.size(24.dp)
                            )
                        }
                    }
    
                    Spacer(GlanceModifier.width(12.dp))
    
                    // 2. text info (middle, expands to fill space)
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = artist,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
    
                    Spacer(GlanceModifier.width(8.dp))
    
                    // 3. controls (right side)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.End
                    ) {
                        // previous button
                        Image(
                            provider = ImageProvider(R.drawable.ic_skip_previous),
                            contentDescription = context.getString(R.string.desc_previous),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier
                                .size(36.dp)
                                .clickable(actionRunCallback<SkipPrevAction>())
                                .padding(6.dp)
                        )
    
                        Spacer(GlanceModifier.width(4.dp))
    
                        // play/pause button (filled circle style)
                        Box(
                            modifier = GlanceModifier
                                .size(48.dp)
                                .background(GlanceTheme.colors.primaryContainer)
                                .cornerRadius(24.dp)
                                .clickable(actionRunCallback<PlayPauseAction>()),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                            val descId = if (isPlaying) R.string.desc_pause else R.string.desc_play
    
                            Image(
                                provider = ImageProvider(iconId),
                                contentDescription = context.getString(descId),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                                modifier = GlanceModifier.size(28.dp)
                            )
                        }
    
                        Spacer(GlanceModifier.width(4.dp))
    
                        // next button
                        Image(
                            provider = ImageProvider(R.drawable.ic_skip_next),
                            contentDescription = context.getString(R.string.desc_next),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier
                                .size(36.dp)
                                .clickable(actionRunCallback<SkipNextAction>())
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    
        @Composable
        private fun rememberBitmapFromPath(path: String): Bitmap? {
            if (path.isEmpty()) return null
            return try {
                BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                null
            }
        }
    }


