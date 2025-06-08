package com.tubes.purry.ui.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.tubes.purry.data.model.Song
import android.util.Log
import com.tubes.purry.utils.MusicNotificationService
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import android.os.Handler
import android.os.Looper
import java.util.Timer
import kotlin.concurrent.timerTask

object PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    private var isPrepared = false
    private var currentlyPlaying: Song? = null
    private var progressUpdateTimer: Timer? = null

    var onCompletion: (() -> Unit)? = null
    var onPrepared: (() -> Unit)? = null
    var onDurationReady: ((Int) -> Unit)? = null
    var onSeeked: ((Int) -> Unit)? = null
    var onPlayerProgressChanged: ((Int) -> Unit)? = null // ADD THIS LINE for progress updates

    private const val TAG = "PlayerController" // Define TAG for consistent logging

    fun getCurrentSong(): Song? {
        return currentlyPlaying
    }

    fun play(song: Song, context: Context): Boolean {
        Log.d("PlayerController", "Masuk play(), judul: ${song.title}, url: ${song.filePath}")

        if (currentlyPlaying?.id == song.id) {
            if (isPlaying()) {
                Log.d("PlayerController", "Lagu sudah diputar")
                return true
            } else {
                Log.d("PlayerController", "Lagu sama tapi belum play, lanjut prepare")
            }
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

                setOnPreparedListener { mp ->
                    Log.d("PlayerController", "onPrepared terpanggil, mulai putar lagu")
                    isPreparing = false
                    isPrepared = true
                    start()

                    val duration = mp.duration
                    Log.d("PlayerController", "Duration ready: $duration ms")
                    onDurationReady?.invoke(duration)
                    onPrepared?.invoke()

                    startProgressUpdater()
                    context.startService(Intent(context, MusicNotificationService::class.java))
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

                try {
                    val path = song.filePath ?: ""
                    when {
                        path.startsWith("http") -> {
                            Log.d("PlayerController", "Streaming dari URL: $path")
                            setDataSource(path)
                        }
                        path.startsWith("content://") -> {
                            Log.d("PlayerController", "Memutar dari content:// URI")
                            val uri = Uri.parse(path)
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            } ?: throw IllegalArgumentException("AssetFileDescriptor null untuk URI: $uri")
                        }
                        path.isNotBlank() -> {
                            val file = File(path)
                            if (!file.exists()) {
                                Log.e("PlayerController", "❌ File tidak ditemukan: $path")
                                throw FileNotFoundException("File not found at $path")
                            }
                            if (!file.canRead()) {
                                Log.e("PlayerController", "❌ File tidak bisa dibaca: $path")
                            }
                            Log.d("PlayerController", "✅ File ditemukan. Memutar dari lokal: ${file.absolutePath}, size: ${file.length()}")

                            val fis = FileInputStream(file)
                            try {
                                setDataSource(fis.fd)
                                Log.d("PlayerController", "✅ setDataSource berhasil untuk file lokal")
                            } catch (e: Exception) {
                                Log.e("PlayerController", "❌ Gagal setDataSource dari file: ${e.message}")
                                throw e
                            } finally {
                                fis.close()
                            }

                        }

                        else -> throw IllegalArgumentException("filePath kosong atau tidak valid")
                    }
                } catch (e: Exception) {
                    Log.e("PlayerController", "Gagal set data source: ${e.message}")
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

    fun stop() {
        try {
            if (mediaPlayer != null) {
                if (isPlaying()) {
                    mediaPlayer?.stop()
                }
                Log.d("PlayerController", "Playback stopped")
            }
            mediaPlayer = null
            currentlyPlaying = null
            isPrepared = false
            isPreparing = false
        } catch (e: Exception) {
            Log.e("PlayerController", "Stop error: ${e.message}")
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

    fun getCurrentPosition(): Int {
        return try {
            if (isPrepared && mediaPlayer != null) {
                val position = mediaPlayer?.currentPosition ?: 0
//                Log.d("PlayerController", "getCurrentPosition: $position ms")
                position
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("PlayerController", "Error getting position: ${e.message}")
            0
        }
    }

    fun getDuration(): Int {
        return try {
            if (isPrepared && mediaPlayer != null) {
                val duration = mediaPlayer?.duration ?: 0
//                Log.d("PlayerController", "getDuration: $duration ms")
                duration
            } else {
                Log.d("PlayerController", "getDuration: not prepared or null player")
                0
            }
        } catch (e: Exception) {
            Log.e("PlayerController", "Error getting duration: ${e.message}")
            0
        }
    }

    fun seekTo(position: Int) {
        try {
            if (isPrepared && mediaPlayer != null) {
                mediaPlayer?.seekTo(position)
//                Log.d("PlayerController", "Seeked to: $position ms")
                onSeeked?.invoke(position)
            } else {
                Log.w("PlayerController", "Cannot seek: not prepared or null player")
            }
        } catch (e: Exception) {
            Log.e("PlayerController", "Error seeking: ${e.message}")
        }
    }


    fun ensureDurationAvailable(): Int {
        return try {
            if (isPrepared && mediaPlayer != null) {
                val duration = mediaPlayer?.duration ?: 0
                if (duration > 0) {
                    Log.d("PlayerController", "Duration ensured: $duration ms")
                    duration
                } else {
                    Log.w("PlayerController", "Duration still not available")
                    0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("PlayerController", "Error ensuring duration: ${e.message}")
            0
        }
    }

    // ADD THESE FUNCTIONS FOR PERIODIC PROGRESS UPDATES
    private fun startProgressUpdater() {
        stopProgressUpdater() // Ensure only one updater is running
        val handler = Handler(Looper.getMainLooper())
        progressUpdateTimer = Timer()
        progressUpdateTimer?.schedule(timerTask {
            handler.post {
                if (mediaPlayer?.isPlaying == true) {
                    val currentPosition = mediaPlayer?.currentPosition ?: 0
                    // This callback will now send updates to MusicNotificationService
                    onPlayerProgressChanged?.invoke(currentPosition)
                    Log.d(TAG, "Progress update pushed: $currentPosition ms") // ADD THIS LOG
                }
            }
        }, 0, 1000) // Update every 1 second (1000ms)
        Log.d(TAG, "Progress updater started.") // ADD THIS LOG
    }

    private fun stopProgressUpdater() {
        progressUpdateTimer?.cancel()
        progressUpdateTimer = null
        Log.d(TAG, "Progress updater stopped.") // ADD THIS LOG
    }
}