package com.tubes.purry.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.tubes.purry.R
import com.tubes.purry.data.model.Song
import com.tubes.purry.MainActivity

object MediaNotificationManager {
    private const val CHANNEL_ID = "purry_music_channel"
    private const val NOTIFICATION_ID = 1

    fun createNotificationChannel(context: Context) {
        Log.w("MediaNotificationManager", "createNotificationChannel called")
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Purry Music Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Menampilkan kontrol pemutar musik di notifikasi"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showNotification(
        context: Context,
        sessionToken: MediaSessionCompat.Token,
        song: Song,
        coverBitmap: Bitmap?,
        isPlaying: Boolean
    ) {
        Log.w("MediaNotificationManager", "showNotification called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("MediaNotificationManager", "Notification permission not granted.")
            return
        }

        val notification = buildNotification(context, sessionToken, song, coverBitmap, isPlaying)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }


    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(
        context: Context,
        sessionToken: MediaSessionCompat.Token,
        song: Song,
        coverBitmap: Bitmap?,
        isPlaying: Boolean
    ): Notification {
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(coverBitmap)
            .setContentIntent(createContentIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_skip_previous, "Prev",
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            )
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PLAY_PAUSE)
            )
            .addAction(
                R.drawable.ic_skip_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            )
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP)
            )
            .setStyle(mediaStyle)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .build()
    }

    private fun createContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
