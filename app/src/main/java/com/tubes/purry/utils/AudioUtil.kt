package com.tubes.purry.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

fun extractAudioMetadata(context: Context, uri: Uri): AudioMetadata {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(context, uri)

    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    val duration = durationStr?.toIntOrNull() ?: 0

    retriever.release()

    return AudioMetadata(title, artist, duration)
}