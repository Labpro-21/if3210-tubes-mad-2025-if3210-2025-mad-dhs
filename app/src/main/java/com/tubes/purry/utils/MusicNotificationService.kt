package com.tubes.purry.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.MainActivity
import com.tubes.purry.ui.player.PlayerController
import android.support.v4.media.MediaMetadataCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "purrytify_channel"
        const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSessionCompat? = null
    private val updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    // Media Session Callback
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d("MediaSession", "onPlay called")
            val intent = Intent(this@MusicNotificationService, MusicNotificationReceiver::class.java).apply {
                action = MusicNotificationReceiver.ACTION_PLAY
            }
            sendBroadcast(intent)
        }

        override fun onPause() {
            Log.d("MediaSession", "onPause called")
            val intent = Intent(this@MusicNotificationService, MusicNotificationReceiver::class.java).apply {
                action = MusicNotificationReceiver.ACTION_PAUSE
            }
            sendBroadcast(intent)
        }

        override fun onSkipToNext() {
            Log.d("MediaSession", "onSkipToNext called")
            val intent = Intent(this@MusicNotificationService, MusicNotificationReceiver::class.java).apply {
                action = MusicNotificationReceiver.ACTION_NEXT
            }
            sendBroadcast(intent)
        }

        override fun onSkipToPrevious() {
            Log.d("MediaSession", "onSkipToPrevious called")
            val intent = Intent(this@MusicNotificationService, MusicNotificationReceiver::class.java).apply {
                action = MusicNotificationReceiver.ACTION_PREV
            }
            sendBroadcast(intent)
        }

        override fun onSeekTo(pos: Long) {
            Log.d("MediaSession", "onSeekTo called: $pos")
            PlayerController.seekTo(pos.toInt())
            updatePlaybackState()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicNotification", "Service dimulai")
        createNotificationChannel()

        val currentSong = PlayerController.getCurrentSong()
        if (currentSong == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        mediaSession = MediaSessionCompat(this, "PurrytifySession").apply {
            setCallback(mediaSessionCallback)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                        MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
            )
            isActive = true
        }

        PlayerController.onSeeked = { position ->
            Log.d("MusicNotification", "Seeked callback ke posisi: $position")
            updatePlaybackState()
        }

        // Update playback state dan mulai update berkala
        updatePlaybackState()
        startPeriodicUpdates()

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
            delay(300)
            createAndShowNotification(currentSong, artworkBitmap)
        }

        return START_STICKY
    }

    private fun createAndShowNotification(song: com.tubes.purry.data.model.Song, artworkBitmap: Bitmap) {
        // Setup metadata dulu sebelum notification
        setupMediaMetadata(song, artworkBitmap)

        val isPlaying = PlayerController.isPlaying()
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseAction = if (isPlaying) MusicNotificationReceiver.ACTION_PAUSE else MusicNotificationReceiver.ACTION_PLAY

        val intentToApp = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intentToApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
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
                    .setShowCancelButton(true)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupMediaMetadata(song: com.tubes.purry.data.model.Song, artworkBitmap: Bitmap) {
        val duration = PlayerController.getDuration().toLong()

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artworkBitmap)
            .build()

        mediaSession?.setMetadata(metadata)
        Log.d("MediaMetadata", "Set metadata - Duration: $duration ms")
    }

    private fun updatePlaybackState() {
        val currentPosition = PlayerController.getCurrentPosition().toLong()
        val duration = PlayerController.getDuration().toLong()
        val isPlaying = PlayerController.isPlaying()

        Log.d("PlaybackState", "Position: $currentPosition, Duration: $duration, Playing: $isPlaying")

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                currentPosition,
                if (isPlaying) 1.0f else 0.0f, // playback speed
                System.currentTimeMillis() // last position update time
            )
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    private fun startPeriodicUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                updatePlaybackState()
                // Update setiap 500ms untuk seekbar yang lebih smooth
                updateHandler.postDelayed(this, 500)
            }
        }
        updateHandler.post(updateRunnable!!)
    }

    private fun stopPeriodicUpdates() {
        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
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
        stopPeriodicUpdates()
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        Log.d("MusicNotification", "Service dihentikan")
    }
}