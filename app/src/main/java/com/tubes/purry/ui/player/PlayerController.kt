package com.tubes.purry.ui.player

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.support.v4.media.session.MediaSessionCompat
import com.tubes.purry.data.model.Song
import android.util.Log
import androidx.core.net.toUri
import com.tubes.purry.R
import com.tubes.purry.utils.MediaNotificationManager


object PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    private var currentlyPlaying: Song? = null
    var onCompletion: (() -> Unit)? = null

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        mediaSession = MediaSessionCompat(appContext, "PurryPlayer")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onStop() = release()
        })
        mediaSession.isActive = true

        MediaNotificationManager.createNotificationChannel(appContext)
    }


    fun play(song: Song, context: Context): Boolean {
        if (!::mediaSession.isInitialized) initialize(context)

        if (currentlyPlaying?.id == song.id && isPlaying()) {
            Log.d("PlayerController", "Same song already playing.")
            return true
        }

        if (isPreparing) {
            Log.d("PlayerController", "Still preparing previous song. Skipping.")
            return false
        }

        Log.d("PlayerController", "Preparing song: ${song.title}")
        release()
        isPreparing = true
        currentlyPlaying = song

        try {
            val coverBitmap = when {
                song.coverResId != null -> BitmapFactory.decodeResource(context.resources, song.coverResId)
                !song.coverPath.isNullOrBlank() -> BitmapFactory.decodeFile(song.coverPath)
                else -> BitmapFactory.decodeResource(context.resources, R.drawable.album_default)
            }

            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener {
                    Log.d("PlayerController", "Prepared, starting playback: ${song.title}")
                    isPreparing = false
                    start()

                    // Show media notification
                    MediaNotificationManager.showNotification(
                        context,
                        mediaSession.sessionToken,
                        song,
                        coverBitmap,
                        isPlaying = true
                    )
                }

                setOnCompletionListener {
                    Log.d("PlayerController", "Playback completed for: ${song.title}")
                    onCompletion?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("PlayerController", "MediaPlayer error: what=$what, extra=$extra")
                    isPreparing = false
                    release()
                    true
                }

                when {
                    song.resId != null -> {
                        val afd = appContext.resources.openRawResourceFd(song.resId)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                    !song.filePath.isNullOrBlank() -> {
                        try {
                            setDataSource(appContext, song.filePath.toUri())
                        } catch (e: SecurityException) {
                            Log.e("PlayerController", "SecurityException: ${e.message}")
                            isPreparing = false
                            release()
                            return false
                        }
                    }
                    else -> {
                        Log.e("PlayerController", "Song has no valid source.")
                        isPreparing = false
                        release()
                        return false
                    }
                }

                prepareAsync()
            }

            return true // sukses jika tidak kena exception
        } catch (e: Exception) {
            Log.e("PlayerController", "Error playing song: ${e.message}")
            isPreparing = false
            release()
            return false
        }
    }

    fun pause() {
        if (isPlaying()) {
            mediaPlayer?.pause()
            Log.d("PlayerController", "Playback paused")

            currentlyPlaying?.let { song ->
                MediaNotificationManager.showNotification(
                    appContext,
                    mediaSession.sessionToken,
                    song,
                    getCurrentCoverBitmap(appContext, song),
                    isPlaying = false
                )
            }
        }
    }

    fun resume() {
        if (!isPlaying()) {
            mediaPlayer?.start()
            Log.d("PlayerController", "Playback resumed")
        }

        currentlyPlaying?.let { song ->
            MediaNotificationManager.showNotification(
                appContext,
                mediaSession.sessionToken,
                song,
                getCurrentCoverBitmap(appContext, song),
                isPlaying = true
            )
        }
    }

    fun release() {
        try {
            Log.d("PlayerController", "Releasing media player")
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w("PlayerController", "MediaPlayer release error: ${e.message}")
        } finally {
            mediaPlayer = null
            isPreparing = false
            currentlyPlaying = null

            if (::mediaSession.isInitialized) mediaSession.isActive = false
            if (::appContext.isInitialized) {
                MediaNotificationManager.dismissNotification(appContext)
            }
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    private fun getCurrentCoverBitmap(context: Context, song: Song) = when {
        song.coverResId != null -> BitmapFactory.decodeResource(context.resources, song.coverResId)
        !song.coverPath.isNullOrBlank() -> BitmapFactory.decodeFile(song.coverPath)
        else -> BitmapFactory.decodeResource(context.resources, R.drawable.album_default)
    }
}