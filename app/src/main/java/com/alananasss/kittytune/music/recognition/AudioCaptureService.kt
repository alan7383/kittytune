package com.alananasss.kittytune.music.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R

class AudioCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.alananasss.kittytune.START_AUDIO_CAPTURE"
        const val ACTION_STOP = "com.alananasss.kittytune.STOP_AUDIO_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        const val CHANNEL_ID = "kittytune_audio_recognition"
        const val NOTIFICATION_ID = 2001
        const val RESULT_NOTIFICATION_ID = 2002

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun showResultNotification(context: Context, title: String, artist: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            createNotificationChannel(context)

            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.recognition_notif_channel))
                .setContentText(context.getString(R.string.recognition_notif_track_found, title, artist))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.recognition_notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.recognition_notif_channel_desc)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    private var mediaProjection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode != 0 && data != null) {
                    startForegroundWithNotification()
                    initMediaProjection(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                cleanupAndStop()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel(this)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.recognition_notif_listening_device))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.recognition_notif_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpManager == null) {
            AudioCaptureManager.notifyError("MediaProjectionManager not available")
            cleanupAndStop()
            return
        }

        try {
            val mp = mpManager.getMediaProjection(resultCode, data)
            if (mp != null) {
                mediaProjection = mp
                AudioCaptureManager.notifyProjectionReady(mp)
            } else {
                AudioCaptureManager.notifyError("Failed to obtain MediaProjection")
                cleanupAndStop()
            }
        } catch (e: Exception) {
            AudioCaptureManager.notifyError(e.message ?: "Failed to get MediaProjection")
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
        AudioCaptureManager.notifyProjectionStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupAndStop()
    }
}
