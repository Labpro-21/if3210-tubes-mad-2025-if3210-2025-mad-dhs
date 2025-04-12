package com.tubes.purry.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.tubes.purry.R
import java.io.InputStream
import androidx.core.content.edit

fun seedAssets(context: Context) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    if (prefs.getBoolean("assets_seeded", false)) return

    // Seed audio
    context.resources.openRawResource(R.raw.monokrom_audio).use { audioStream ->
        saveMediaToExternalStorage(
            context, "monokrom.mp3", "audio/mp3", audioStream, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )
    }

    context.resources.openRawResource(R.raw.tak_ingin_pisah_lagi_audio).use { audioStream ->
        saveMediaToExternalStorage(
            context, "tak_ingin_pisah_lagi.mp3", "audio/mp3", audioStream, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )
    }

    // Seed image
    context.resources.openRawResource(R.raw.monokrom_cover).use { imageStream ->
        saveMediaToExternalStorage(
            context, "monokrom.jpg", "image/jpg", imageStream, MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
    }

    context.resources.openRawResource(R.raw.tak_ingin_pisah_lagi_cover).use { imageStream ->
        saveMediaToExternalStorage(
            context, "tak_ingin_pisah_lagi.png", "image/png", imageStream, MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
    }

    prefs.edit { putBoolean("assets_seeded", true) }
}

fun saveMediaToExternalStorage(
    context: Context,
    displayName: String,
    mimeType: String,
    inputStream: InputStream,
    collection: Uri
) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH,
            if (mimeType.startsWith("audio")) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_PICTURES)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val itemUri = resolver.insert(collection, contentValues) ?: return
    resolver.openOutputStream(itemUri)?.use { outputStream ->
        inputStream.copyTo(outputStream)
    }

    contentValues.clear()
    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
    resolver.update(itemUri, contentValues, null, null)
}
