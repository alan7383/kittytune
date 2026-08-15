    package com.alananasss.kittytune.ui.widget

    import android.content.Context
    import android.content.Intent
    import android.os.Build
    import androidx.glance.GlanceId
    import androidx.glance.action.ActionParameters
    import androidx.glance.appwidget.action.ActionCallback
    import com.alananasss.kittytune.data.AchievementManager
    import com.alananasss.kittytune.data.MusicManager
    import com.alananasss.kittytune.data.PlaybackService
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext

    val ActionSpeedKey = ActionParameters.Key<Float>("speed_value")
    val ActionEffectKey = ActionParameters.Key<String>("effect_type")

    class PlayPauseAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val controller = getMediaController(context)
            if (controller?.isPlaying == true) controller.pause() else controller?.play()
            controller?.release()
        }
    }

    class SkipNextAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val controller = getMediaController(context)
            controller?.seekToNext()
            controller?.release()
        }
    }

    class SkipPrevAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val controller = getMediaController(context)
            controller?.seekToPrevious()
            controller?.release()
        }
    }

    class ToggleLikeAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val controller = getMediaController(context)
            controller?.sendCustomCommand(androidx.media3.session.SessionCommand(PlaybackService.CUSTOM_ACTION_LIKE, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY)
            controller?.release()
        }
    }

    class OpenAppAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    class SetSpecificSpeedAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val targetSpeed = parameters[ActionSpeedKey] ?: 1.0f

            withContext(Dispatchers.Main) {
                val prefs = PlayerPreferences(context)
                val current = prefs.getLastEffects()

                // if clicking the speed already active, reset to 1.0, otherwise apply target
                val newSpeed = if (current.speed == targetSpeed) 1.0f else targetSpeed

                val newState = current.copy(speed = newSpeed)
                MusicManager.applyEffects(newState)
                prefs.saveEffects(newState)

                // if fast speed, achievement
                if (newSpeed >= 1.2f) AchievementManager.increment("speed_demon", 1)

                MusicWidget.update(context)
            }
        }
    }

    class ToggleSpecificEffectAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val effectType = parameters[ActionEffectKey] ?: return

            withContext(Dispatchers.Main) {
                val prefs = PlayerPreferences(context)
                val current = prefs.getLastEffects()
                var newState = current

                when(effectType) {
                    "BASS" -> {
                        newState = current.copy(isBassBoostEnabled = !current.isBassBoostEnabled, isMuffledEnabled = false)
                        if(newState.isBassBoostEnabled) AchievementManager.increment("bass_addict", 1)
                    }
                    "8D" -> newState = current.copy(is8DEnabled = !current.is8DEnabled)
                    "MUFFLED" -> newState = current.copy(isMuffledEnabled = !current.isMuffledEnabled, isBassBoostEnabled = false)
                    "REVERB" -> newState = current.copy(isReverbEnabled = !current.isReverbEnabled)
                    "PITCH" -> newState = current.copy(isPitchEnabled = !current.isPitchEnabled)
                }

                MusicManager.applyEffects(newState)
                prefs.saveEffects(newState)
                MusicWidget.update(context)
            }
        }
    }

    // new action to open the app directly to the search screen
    class OpenSearchAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                // add a special extra to tell the activity what to do
                putExtra("open_search", true)
            }
            context.startActivity(intent)
        }
    }

    private suspend fun getMediaController(context: Context): androidx.media3.session.MediaController? {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val sessionToken = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))
                val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
                controllerFuture.get()
            }
        } catch (e: Exception) {
            null
        }
    }

