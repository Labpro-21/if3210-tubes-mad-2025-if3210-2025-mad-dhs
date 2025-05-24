package com.tubes.purry.ui.player

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.tubes.purry.data.model.Song
import android.util.Log
import androidx.core.net.toUri
import com.tubes.purry.ui.player.NowPlayingViewModel.RepeatMode


object PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    private var isPrepared = false
    private var currentlyPlaying: Song? = null

    var onCompletion: (() -> Unit)? = null
    var onPrepared: (() -> Unit)? = null

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
        isPrepared = false
        currentlyPlaying = song

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )

                setOnPreparedListener {
                    Log.d("PlayerController", "onPrepared terpanggil, mulai putar lagu")
                    isPreparing = false
                    isPrepared = true
                    start()
                    onPrepared?.invoke()
                }

                setOnCompletionListener {
                    Log.d("PlayerController", "Lagu selesai diputar")
                    onCompletion?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("PlayerController", "Error MediaPlayer: what=$what, extra=$extra")
                    isPreparing = false
                    isPrepared = false
                    release()
                    true
                }

                if (!song.filePath.isNullOrBlank()) {
                    if (song.filePath.startsWith("http")) {
                        Log.d("PlayerController", "Streaming dari URL: ${song.filePath}")
                        setDataSource(song.filePath)
                    } else {
                        Log.d("PlayerController", "Memutar dari file lokal: ${song.filePath}")
                        setDataSource(song.filePath) // <- gunakan ini untuk path lokal
                    }
                } else {
                    Log.e("PlayerController", "filePath kosong")
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
            isPrepared = false
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
        if (!isPlaying() && isPrepared) {
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
            isPrepared = false
            currentlyPlaying = null
        }
    }

    fun isPlaying(): Boolean {
        return isPrepared && mediaPlayer?.isPlaying == true
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = if (isPrepared) mediaPlayer?.duration ?: 0 else 0

    fun seekTo(position: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(position)
        }
    }
}