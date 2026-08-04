package com.cheminsdhistoire.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cheminsdhistoire.app.HistoireApp
import com.cheminsdhistoire.app.MainActivity
import com.cheminsdhistoire.app.R
import com.cheminsdhistoire.app.model.PlaybackState
import com.cheminsdhistoire.app.playback.PlaybackController
import kotlinx.coroutines.launch

/**
 * Service de premier plan : maintient le processus vivant pour que le GPS et la
 * lecture audio continuent même écran éteint, en voiture. Idéal pour l'écoute continue.
 */
class PlaybackService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        PlaybackController.init(this)
        startForegroundWith(getString(R.string.notif_default_text))

        // Met à jour la notification avec le récit en cours.
        lifecycleScope.launch {
            PlaybackController.state.collect { s ->
                val text = when (s.playbackState) {
                    PlaybackState.SPEAKING -> "À l'écoute : ${s.currentStory?.title ?: ""}"
                    PlaybackState.GENERATING -> "Écriture du récit…"
                    PlaybackState.SEARCHING -> "Recherche de lieux historiques…"
                    PlaybackState.PAUSED -> "En pause"
                    else -> getString(R.string.notif_default_text)
                }
                updateNotification(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            PlaybackController.stopEngine()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        PlaybackController.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, HistoireApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_default_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startForegroundWith(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(text), type)
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.cheminsdhistoire.app.STOP"

        fun start(context: Context) {
            val i = Intent(context, PlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, PlaybackService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
