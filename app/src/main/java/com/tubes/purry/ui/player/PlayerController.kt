package com.tubes.purry.ui.player

import android.content.Context
import android.media.MediaPlayer
import com.tubes.purry.data.model.Song
import android.util.Log
import androidx.core.net.toUri


object PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    private var currentlyPlaying: Song? = null

    fun play(song: Song, context: Context) {
        if (currentlyPlaying?.id == song.id && isPlaying()) {
            Log.d("PlayerController", "Same song already playing.")
            return
        }

        if (isPreparing) {
            Log.d("PlayerController", "Still preparing previous song. Skipping.")
            return
        }

        Log.d("PlayerController", "Preparing song: ${song.title}")
        this.release()
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
                    release()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("PlayerController", "MediaPlayer error: what=$what, extra=$extra")
                    isPreparing = false
                    release()

                    // Optional: Retry once for transient errors
                    // Handler(Looper.getMainLooper()).postDelayed({
                    //     play(song, appContext)
                    // }, 500)

                    true
                }

                when {
                    song.resId != null -> {
                        val afd = appContext.resources.openRawResourceFd(song.resId)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                    !song.filePath.isNullOrBlank() -> {
                        setDataSource(appContext, song.filePath.toUri())
//                        val uri = song.filePath.toUri()
//                        val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
//                        if (afd != null) {
//                            setDataSource(afd.fileDescriptor)
//                            afd.close()
//                        } else {
//                            Log.e("PlayerController", "Failed to open AssetFileDescriptor for song URI.")
//                            return
//                        }
                    }
                    else -> {
                        Log.e("PlayerController", "Song has no valid source.")
                        isPreparing = false
                        release()
                        return
                    }
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("PlayerController", "Error playing song: ${e.message}")
            isPreparing = false
            this.release()
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

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }
}