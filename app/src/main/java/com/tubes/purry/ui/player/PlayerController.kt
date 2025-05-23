package com.tubes.purry.ui.player

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.net.Uri
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
        Log.d("PlayerController", "Masuk play(), judul: ${song.title}, url: ${song.filePath}")

        if (currentlyPlaying?.id == song.id && isPlaying()) {
            Log.d("PlayerController", "Lagu sudah diputar")
            return true
        }

        if (isPreparing) {
            Log.d("PlayerController", "Masih mempersiapkan lagu sebelumnya")
            return false
        }

        release()
        isPreparing = true
        currentlyPlaying = song

        try {
            val appContext = context.applicationContext
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC) // PENTING

                setOnPreparedListener {
                    Log.d("PlayerController", "onPrepared terpanggil, mulai putar lagu")
                    isPreparing = false
                    start()
                }

                setOnCompletionListener {
                    Log.d("PlayerController", "Lagu selesai diputar")
                    onCompletion?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("PlayerController", "Error MediaPlayer: what=$what, extra=$extra")
                    isPreparing = false
                    release()
                    true
                }

                try {
                    if (!song.filePath.isNullOrBlank()) {
                        Log.d("PlayerController", "Memanggil setDataSource dengan URL")
                        setDataSource(song.filePath)
                    } else {
                        Log.e("PlayerController", "filePath kosong")
                        isPreparing = false
                        release()
                        return false
                    }
                } catch (e: Exception) {
                    Log.e("PlayerController", "Gagal setDataSource: ${e.message}")
                    isPreparing = false
                    release()
                    return false
                }

                Log.d("PlayerController", "Memanggil prepareAsync()")
                prepareAsync()
            }

            return true
        } catch (e: Exception) {
            Log.e("PlayerController", "Exception luar: ${e.message}")
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
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w("PlayerController", "Release error: ${e.message}")
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
