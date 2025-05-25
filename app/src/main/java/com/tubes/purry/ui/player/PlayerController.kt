package com.tubes.purry.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.tubes.purry.data.model.Song
import android.util.Log
import androidx.core.net.toUri
import com.tubes.purry.R
import com.tubes.purry.utils.MediaNotificationManager

object PlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    var currentlyPlaying: Song? = null // Made public for easier access if needed by ViewModel
    var onCompletion: (() -> Unit)? = null
    var onNextSong: (() -> Unit)? = null // Callback for next
    var onPreviousSong: (() -> Unit)? = null // Callback for previous

    private lateinit var mediaSession: MediaSessionCompat
    internal lateinit var appContext: Context // Keep a reference to application context

    // To be called from Application class or MainActivity's onCreate
    fun initialize(context: Context) {
        if (::appContext.isInitialized) return // Already initialized

        Log.d("PlayerController", "Initializing PlayerController and MediaSession...")
        appContext = context.applicationContext // Use application context

        mediaSession = MediaSessionCompat(appContext, "PurryPlayerSession")

        // Set the callback to handle media button events
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d("MediaSessionCallback", "onPlay called")
                resume()
            }

            override fun onPause() {
                Log.d("MediaSessionCallback", "onPause called")
                pause()
            }

            override fun onStop() {
                Log.d("MediaSessionCallback", "onStop called")
                release() // This will stop playback and dismiss the notification
            }

            override fun onSkipToNext() {
                Log.d("MediaSessionCallback", "onSkipToNext called")
                onNextSong?.invoke()
            }

            override fun onSkipToPrevious() {
                Log.d("MediaSessionCallback", "onSkipToPrevious called")
                onPreviousSong?.invoke()
            }
        })

        // It's important to set the session active *after* setting the callback and flags.
        mediaSession.isActive = true
        Log.d("PlayerController", "MediaSession initialized and active.")

        MediaNotificationManager.createNotificationChannel(appContext)
    }

    fun isMediaSessionActive(): Boolean {
        return ::mediaSession.isInitialized && mediaSession.isActive
    }

    fun getMediaSessionCompat(): MediaSessionCompat {
        if (!::mediaSession.isInitialized) {
            Log.e("PlayerController", "getMediaSessionCompat() called but mediaSession is not initialized!")
            // Attempt a last-ditch effort to initialize if appContext is available
            if(::appContext.isInitialized) {
                initialize(appContext)
            } else {
                throw IllegalStateException("MediaSession accessed before PlayerController was initialized with a context.")
            }
        }
        return mediaSession
    }

    fun play(song: Song, context: Context): Boolean {
        // Ensure initialized (especially if app was killed and restarted by media button)
        if (!::appContext.isInitialized) {
            initialize(context.applicationContext)
        }
        if (!::mediaSession.isInitialized || !mediaSession.isActive) {
            Log.w("PlayerController", "MediaSession was not active. Re-initializing lightly.")
            // A lighter re-init if appContext is there but session isn't active
            mediaSession = MediaSessionCompat(appContext, "PurryPlayerSession")

            mediaSession.setCallback(PlayerSessionCallback()) // Re-set callback
            mediaSession.isActive = true
        }


        if (currentlyPlaying?.id == song.id && isPlaying()) {
            Log.d("PlayerController", "Same song already playing: ${song.title}")
            updateMediaSessionState(isPlaying = true) // Ensure metadata and state are fresh
            showOrUpdateNotification(song, isPlaying = true)
            return true
        }

        if (isPreparing) {
            Log.d("PlayerController", "Still preparing previous song. Skipping play request for: ${song.title}")
            return false
        }

        Log.d("PlayerController", "Preparing to play song: ${song.title}")
        releaseMediaPlayer() // Release only MediaPlayer, not the session
        isPreparing = true
        currentlyPlaying = song

        try {
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener {
                    Log.d("PlayerController", "MediaPlayer Prepared for: ${song.title}. Starting playback.")
                    isPreparing = false
                    start()
                    updateMediaSessionState(isPlaying = true)
                    showOrUpdateNotification(song, isPlaying = true)
                }

                setOnCompletionListener {
                    Log.d("PlayerController", "MediaPlayer Playback completed for: ${song.title}")
                    // If not repeating one, update state to paused before invoking onCompletion
                    // which might trigger next song.
                    if (PlayerSessionCallback.currentRepeatMode != NowPlayingViewModel.RepeatMode.ONE) {
                        updateMediaSessionState(isPlaying = false) // Reflect that it's not actively playing this track anymore
                    }
                    onCompletion?.invoke()
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e("PlayerController", "MediaPlayer error: what=$what, extra=$extra for song: ${song.title}")
                    isPreparing = false
                    releaseMediaPlayer() // Release only player
                    updateMediaSessionState(isPlaying = false, isError = true)
                    // Potentially show error notification or hide current one
                    MediaNotificationManager.dismissNotification(appContext)
                    true // Error handled
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
                            Log.e("PlayerController", "SecurityException setting data source: ${e.message}")
                            isPreparing = false
                            releaseMediaPlayer()
                            updateMediaSessionState(isPlaying = false, isError = true)
                            return false
                        }
                    }
                    else -> {
                        Log.e("PlayerController", "Song ${song.title} has no valid source.")
                        isPreparing = false
                        releaseMediaPlayer()
                        updateMediaSessionState(isPlaying = false, isError = true)
                        return false
                    }
                }
                prepareAsync()
                Log.d("PlayerController", "MediaPlayer.prepareAsync() called for ${song.title}")
            }
            return true
        } catch (e: Exception) {
            Log.e("PlayerController", "Exception during play setup for ${song.title}: ${e.message}", e)
            isPreparing = false
            releaseMediaPlayer()
            updateMediaSessionState(isPlaying = false, isError = true)
            return false
        }
    }

    fun pause() {
        if (isPlaying() && mediaPlayer != null) {
            mediaPlayer?.pause()
            Log.d("PlayerController", "Playback paused for: ${currentlyPlaying?.title}")
            updateMediaSessionState(isPlaying = false)
            currentlyPlaying?.let { showOrUpdateNotification(it, isPlaying = false) }
        }
    }

    fun resume() {
        if (!isPlaying() && mediaPlayer != null && currentlyPlaying != null) {
            mediaPlayer?.start()
            Log.d("PlayerController", "Playback resumed for: ${currentlyPlaying?.title}")
            updateMediaSessionState(isPlaying = true)
            currentlyPlaying?.let { showOrUpdateNotification(it, isPlaying = true) }
        } else if (currentlyPlaying == null && mediaPlayer == null) {
            Log.w("PlayerController", "Resume called but no song was loaded.")
            // Optionally, try to play the last song or first in queue from ViewModel if accessible
        }
    }

    // Call this when the app is fully exiting or player is no longer needed
    fun fullyReleaseSession() {
        Log.d("PlayerController", "Fully releasing MediaSession and MediaPlayer.")
        releaseMediaPlayer()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        if(::appContext.isInitialized) { // Check if appContext was initialized
            MediaNotificationManager.dismissNotification(appContext)
        }
        currentlyPlaying = null
        // Reset callbacks to avoid leaks or unintended calls
        onCompletion = null
        onNextSong = null
        onPreviousSong = null
    }


    // Renamed from 'release' to be more specific
    internal fun releaseMediaPlayer() {
        Log.d("PlayerController", "Releasing MediaPlayer instance.")
        mediaPlayer?.release()
        mediaPlayer = null
        isPreparing = false
        // Don't nullify currentlyPlaying here, session might still need it for metadata
        // Only nullify currentlyPlaying when the session itself is being stopped/released.
    }

    // This is the main stop point that also clears the notification
    fun release() { // This is effectively the 'stop' action
        Log.d("PlayerController", "PlayerController.release() (stop) called.")
        releaseMediaPlayer()
        updateMediaSessionState(isPlaying = false, isStopped = true)
        if (::appContext.isInitialized) {
            MediaNotificationManager.dismissNotification(appContext)
        }
        currentlyPlaying = null // Now it's safe to clear the current song
        // Consider if MediaSession should be released here or kept for app lifecycle
        // For now, just make it inactive if we are stopping.
        // if (::mediaSession.isInitialized) mediaSession.isActive = false;
    }


    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        // Update MediaSession state after seek if needed, especially if affecting duration display
        updateMediaSessionState(isPlaying = isPlaying())
    }

    private fun updateMediaSessionState(isPlaying: Boolean, isError: Boolean = false, isStopped: Boolean = false) {
        if (!::mediaSession.isInitialized) {
            Log.w("PlayerController", "MediaSession not initialized, cannot update state.")
            return
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP // Ensure stop action is available
            )

        val playbackState: Int = if (isError) {
            PlaybackStateCompat.STATE_ERROR
        } else if (isStopped) {
            PlaybackStateCompat.STATE_STOPPED
        } else {
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        }

        stateBuilder.setState(playbackState, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f)
        mediaSession.setPlaybackState(stateBuilder.build())

        // Update Metadata
        currentlyPlaying?.let { song ->
            val coverBitmap = getCurrentCoverBitmap(appContext, song)
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "") // Add album if you have it
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration.toLong())
            if (coverBitmap != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
            }
            mediaSession.setMetadata(metadataBuilder.build())
        }
        Log.d("PlayerController", "MediaSession state updated: ${if (isPlaying) "Playing" else if (isStopped) "Stopped" else "Paused"}")
    }

    private fun showOrUpdateNotification(song: Song, isPlaying: Boolean) {
        if (!::appContext.isInitialized || !::mediaSession.isInitialized) return

        val coverBitmap = getCurrentCoverBitmap(appContext, song)
        MediaNotificationManager.showNotification(
            appContext,
            mediaSession.sessionToken,
            song,
            coverBitmap,
            isPlaying
        )
    }

    private fun getCurrentCoverBitmap(context: Context, song: Song): Bitmap? {
        return try {
            when {
                song.coverResId != null -> BitmapFactory.decodeResource(context.resources, song.coverResId)
                !song.coverPath.isNullOrBlank() -> {
                    // For content URIs or file paths
                    if (song.coverPath.startsWith("content://")) {
                        context.contentResolver.openInputStream(song.coverPath.toUri())?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }
                    } else { // Assuming it's a file path
                        BitmapFactory.decodeFile(song.coverPath)
                    }
                }
                else -> BitmapFactory.decodeResource(context.resources, R.drawable.album_default)
            }
        } catch (e: Exception) {
            Log.e("PlayerController", "Error loading cover bitmap: ${e.message}")
            BitmapFactory.decodeResource(context.resources, R.drawable.album_default)
        }
    }

    // Inner class for MediaSession.Callback to keep it tidy
    internal class PlayerSessionCallback : MediaSessionCompat.Callback() {
        companion object {
            // Allow NowPlayingViewModel to update this
            var currentRepeatMode: NowPlayingViewModel.RepeatMode = NowPlayingViewModel.RepeatMode.NONE
        }
        override fun onPlay() {
            Log.d("PlayerSessionCallback", "onPlay")
            resume()
        }

        override fun onPause() {
            Log.d("PlayerSessionCallback", "onPause")
            pause()
        }

        override fun onStop() { // This is triggered by setDeleteIntent or an explicit stop command
            Log.d("PlayerSessionCallback", "onStop")
            release() // Calls the modified release which stops and dismisses notification
        }

        override fun onSkipToNext() {
            Log.d("PlayerSessionCallback", "onSkipToNext")
            onNextSong?.invoke()
        }

        override fun onSkipToPrevious() {
            Log.d("PlayerSessionCallback", "onSkipToPrevious")
            onPreviousSong?.invoke()
        }
    }
}