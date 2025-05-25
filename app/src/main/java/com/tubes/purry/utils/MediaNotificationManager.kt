package com.tubes.purry.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver // Correct import
import com.tubes.purry.R
import com.tubes.purry.data.model.Song
import com.tubes.purry.MainActivity // To navigate back to the app

object MediaNotificationManager {
    private const val CHANNEL_ID = "purry_music_channel"
    const val NOTIFICATION_ID = 1 // Make it accessible if needed elsewhere

    // Action for custom stop command
    const val ACTION_STOP_PLAYBACK = "com.tubes.purry.ACTION_STOP_PLAYBACK"

    fun createNotificationChannel(context: Context) {
        Log.w("MediaNotificationManager", "createNotificationChannel called")
        // Channel needed for Oreo+
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Purry Music Playback", // User-visible name
            NotificationManager.IMPORTANCE_LOW // Low importance for ongoing media
        ).apply {
            description = "Displays music controls while playing in the background" // User-visible description
            setSound(null, null) // No sound for media notifications
            enableLights(false)
            enableVibration(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Log.d("MediaNotificationManager", "Notification channel created.")
    }

    fun showNotification(
        context: Context,
        sessionToken: MediaSessionCompat.Token,
        song: Song,
        coverBitmap: Bitmap?,
        isPlaying: Boolean
    ) {
        Log.d("MediaNotificationManager", "Attempting to show notification for: ${song.title}, isPlaying: $isPlaying")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("MediaNotificationManager", "Notification permission not granted. Cannot show notification.")
            return
        }

        // Intent to open MainActivity, then navigate to SongDetailFragment
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // Optional: Add extra to tell MainActivity to navigate to player
            putExtra("NAVIGATE_TO_PLAYER", true)
            putExtra("CURRENT_SONG_ID", song.id)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create media actions
        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (isPlaying) "Pause" else "Play",
            MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        )

        val prevAction = NotificationCompat.Action(
            R.drawable.ic_skip_previous,
            "Prev",
            MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        )

        val nextAction = NotificationCompat.Action(
            R.drawable.ic_skip_next,
            "Next",
            MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )

        // For Dismiss/Stop - This intent will be handled by your MediaButtonReceiver
        val stopPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_STOP
        )


        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Play/Pause, Prev, Next
                // Optional: .setShowCancelButton(true)
                // .setCancelButtonIntent(stopPendingIntent) // For swipe dismiss
            )
            .setSmallIcon(R.drawable.logo) // Your app logo
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(coverBitmap ?: BitmapFactory.decodeResource(context.resources, R.drawable.album_default))
            .setContentIntent(pendingContentIntent)
            .setDeleteIntent(stopPendingIntent) // Called when notification is swiped away or cleared
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on lock screen
            .setOnlyAlertOnce(true) // Don't make sound/vibrate for updates
            .setOngoing(isPlaying) // Makes it non-dismissible by swiping if true
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notificationBuilder.build())
        Log.d("MediaNotificationManager", "Notification displayed/updated for: ${song.title}")
    }

    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        Log.d("MediaNotificationManager", "Notification dismissed.")
    }
}
