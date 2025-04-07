package com.tubes.purry.ui.player

import android.content.Context
import android.media.MediaPlayer
import com.tubes.purry.data.model.Song
import android.util.Log
import androidx.core.net.toUri


object PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false

    fun play(song: Song, context: Context) {
        if (isPreparing) {
            Log.d("PlayerController", "Still preparing previous song. Skipping.")
            return
        }

        release()
        isPreparing = true

        try {
            mediaPlayer = MediaPlayer().apply {
                when {
                    song.resId != null -> {
                        val afd = context.resources.openRawResourceFd(song.resId)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                    !song.filePath.isNullOrBlank() -> {
                        setDataSource(context, song.filePath.toUri())
                    }
                    else -> {
                        Log.e("PlayerController", "Song has no valid source.")
                        return
                    }
                }

                setOnPreparedListener {
                    isPreparing = false
                    it.start()
                }

                setOnCompletionListener {
                    Log.d("PlayerController", "Playback completed")
                    release()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("PlayerController", "MediaPlayer error: what=$what, extra=$extra")
                    isPreparing = false
                    release()
                    true
                }

                prepareAsync() // Prepares in background and triggers onPrepared
            }
        } catch (e: Exception) {
            Log.e("PlayerController", "Error playing song: ${e.message}")
            release()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun resume() {
        mediaPlayer?.start()
    }

    fun release() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w("PlayerController", "MediaPlayer release error: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}