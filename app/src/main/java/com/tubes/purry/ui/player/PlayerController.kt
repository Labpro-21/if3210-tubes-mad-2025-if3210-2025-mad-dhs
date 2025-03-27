package com.tubes.purry.ui.player

import android.media.MediaPlayer

object PlayerController {
    private var mediaPlayer: MediaPlayer? = null

    fun play(filePath: String) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun resume() {
        mediaPlayer?.start()
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}