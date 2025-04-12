package com.tubes.purry.ui.player

import android.content.Context
import android.media.MediaPlayer
import com.tubes.purry.data.model.Song
import android.util.Log
import androidx.core.net.toUri
import com.tubes.purry.ui.player.NowPlayingViewModel.RepeatMode


object PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    private var currentlyPlaying: Song? = null
    var onCompletion: (() -> Unit)? = null

    fun play(song: Song, context: Context): Boolean {
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
            val appContext = context.applicationContext
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener {
                    Log.d("PlayerController", "Prepared, starting playback: ${song.title}")
                    isPreparing = false
                    start()
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
        }
    }

    fun resume() {
        if (!isPlaying()) {
            mediaPlayer?.start()
            Log.d("PlayerController", "Playback resumed")
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
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }
}
