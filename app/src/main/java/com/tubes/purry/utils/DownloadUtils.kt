package com.tubes.purry.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.OnlineSong
import com.tubes.purry.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object DownloadUtils {

    private val client = OkHttpClient()

    fun downloadSong(
        context: Context,
        onlineSong: OnlineSong,
        onComplete: (File?) -> Unit
    ) {
        val fileName = "${onlineSong.title}_${onlineSong.artist}.mp3"
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")

        val dir = File(context.getExternalFilesDir(null), "PurryMusic")
        if (!dir.exists()) dir.mkdirs()

        val destFile = File(dir, fileName)

        if (destFile.exists()) {
            Log.d("DownloadUtils", "File sudah ada: ${destFile.absolutePath}")
            Handler(Looper.getMainLooper()).post {
                onComplete(destFile)
            }
            return
        }

        val request = Request.Builder().url(onlineSong.url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DownloadUtils", "Download gagal: ${e.message}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Download gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete(null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("DownloadUtils", "Response gagal: ${response.code}")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Download gagal: ${response.code}", Toast.LENGTH_SHORT).show()
                        onComplete(null)
                    }
                    return
                }

                try {
                    val input = response.body?.byteStream()
                    val output = FileOutputStream(destFile)

                    input?.copyTo(output)

                    output.flush()
                    output.close()
                    input?.close()

                    Log.d("DownloadUtils", "Download selesai: ${destFile.absolutePath}")

                    val song = Song(
                        id = onlineSong.id.toString(),
                        title = onlineSong.title,
                        artist = onlineSong.artist,
                        filePath = destFile.absolutePath,
                        coverPath = onlineSong.artwork,
                        duration = 0,
                        isLiked = false,
                        isLocal = true,
                        lastPlayedAt = 0L
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        AppDatabase.getDatabase(context).songDao().insert(song)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Berhasil disimpan ke: ${destFile.absolutePath}", Toast.LENGTH_SHORT).show()
                            onComplete(destFile)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("DownloadUtils", "Gagal menyimpan file: ${e.message}")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Gagal menyimpan file", Toast.LENGTH_SHORT).show()
                        onComplete(null)
                    }
                }
            }
        })
    }
}
