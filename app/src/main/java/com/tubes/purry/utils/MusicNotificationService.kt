package com.tubes.purry.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.MainActivity
import com.tubes.purry.ui.player.PlayerController
import kotlinx.coroutines.*

class MusicNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "purrytify_channel"
        const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSessionCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicNotification", "Service dimulai")
        createNotificationChannel()

        val currentSong = PlayerController.getCurrentSong()
        if (currentSong == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val artworkBitmap: Bitmap = withContext(Dispatchers.IO) {
                try {
                    Glide.with(this@MusicNotificationService)
                        .asBitmap()
                        .load(currentSong.coverPath)
                        .submit()
                        .get()
                } catch (e: Exception) {
                    Log.e("MusicNotification", "Gagal load artwork: ${e.message}")
                    BitmapFactory.decodeResource(resources, R.drawable.logo)
                }
            }

            val isPlaying = PlayerController.isPlaying()
            val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            val playPauseAction =
                if (isPlaying) MusicNotificationReceiver.ACTION_PAUSE else MusicNotificationReceiver.ACTION_PLAY

            mediaSession = MediaSessionCompat(this@MusicNotificationService, "PurrytifySession")

            // Setup playback state (agar seekbar aktif)
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlayerController.getCurrentPosition().toLong(),
                    1.0f
                )
                .build()
            mediaSession?.setPlaybackState(playbackState)
            mediaSession?.isActive = true

            val intentToApp = Intent(this@MusicNotificationService, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                this@MusicNotificationService,
                0,
                intentToApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this@MusicNotificationService, CHANNEL_ID)
                .setContentTitle(currentSong.title)
                .setContentText(currentSong.artist)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(artworkBitmap)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_skip_previous, "Prev", getPendingIntent(MusicNotificationReceiver.ACTION_PREV))
                .addAction(playPauseIcon, "Play/Pause", getPendingIntent(playPauseAction))
                .addAction(R.drawable.ic_skip_next, "Next", getPendingIntent(MusicNotificationReceiver.ACTION_NEXT))
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(), // Use unique request code for each action
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Purrytify Music",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Menampilkan kontrol pemutar musik"
            setShowBadge(false)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        Log.d("MusicNotification", "Service dihentikan")
    }
}