package com.tubes.purry.ui.analytics

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.tubes.purry.data.model.AnalyticsExportData
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsExportService(private val context: Context) {

    private fun createExportFileLegacy(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val purritifyDir = File(downloadsDir, "Purritify")
        if (!purritifyDir.exists()) {
            purritifyDir.mkdirs()
        }
        return File(purritifyDir, fileName)
    }

    suspend fun exportPdfToMediaStore(exportData: AnalyticsExportData): Boolean {
        return try {
            val fileName = "purritify_analytics_${exportData.month}.pdf"
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 14f
                typeface = Typeface.MONOSPACE
            }

            var y = 40
            fun drawLine(text: String) {
                canvas.drawText(text, 40f, y.toFloat(), paint)
                y += 20
            }

            drawLine("🎧 Purritify Analytics Export")
            drawLine("Month: ${exportData.month}")
            drawLine("User: ${exportData.userInfo.username}")
            drawLine("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
            y += 20

            drawLine("📊 Monthly Summary:")
            drawLine("Total Minutes: ${exportData.analytics.totalMinutesListened}")
            drawLine("Top Artist: ${exportData.analytics.topArtist ?: "N/A"}")
            drawLine("Top Song: ${exportData.analytics.topSong ?: "N/A"}")
            y += 20

            drawLine("🎵 Top Songs:")
            exportData.topSongs.take(5).forEach {
                drawLine("${it.rank}. ${it.title} - ${it.artist} (${it.playCount} plays)")
            }
            y += 20

            drawLine("🎤 Top Artists:")
            exportData.topArtists.take(5).forEach {
                drawLine("${it.rank}. ${it.name} (${it.playCount} plays)")
            }

            pdfDocument.finishPage(page)

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Purritify")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Failed to create MediaStore record.")

            contentResolver.openOutputStream(uri).use { output ->
                pdfDocument.writeTo(output!!)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)

            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }



    suspend fun exportToCsv(exportData: AnalyticsExportData): Boolean {
        val fileName = "purritify_analytics_${exportData.month}.csv"
        val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createExportFileApi29Plus(fileName)
        } else {
            FileOutputStream(createExportFileLegacy(fileName))
        }

        return try {
            outputStream?.bufferedWriter()?.use { writer ->
                // Header information
                writer.append("Purritify Sound Capsule Export\n")
                writer.append("Month,${exportData.month}\n")
                writer.append("User,${exportData.userInfo.username}\n")
                writer.append("Export Date,${exportData.exportDate}\n\n")

                // Monthly Summary
                writer.append("MONTHLY SUMMARY\n")
                writer.append("Total Minutes Listened,${exportData.analytics.totalMinutesListened}\n")
                writer.append("Daily Average (minutes),${exportData.analytics.dailyAverage}\n")
                writer.append("Total Songs Played,${exportData.analytics.totalSongsPlayed}\n")
                writer.append("Total Artists Listened,${exportData.analytics.totalArtistsListened}\n")
                writer.append("Top Artist,${exportData.analytics.topArtist ?: "N/A"}\n")
                writer.append("Top Artist Play Count,${exportData.analytics.topArtistPlayCount}\n")
                writer.append("Top Song,${exportData.analytics.topSong ?: "N/A"}\n")
                writer.append("Top Song Artist,${exportData.analytics.topSongArtist ?: "N/A"}\n")
                writer.append("Top Song Play Count,${exportData.analytics.topSongPlayCount}\n\n")

                // Daily Chart
                writer.append("DAILY LISTENING CHART\n")
                writer.append("Day,Minutes\n")
                exportData.dailyChart.forEach { dayData ->
                    writer.append("${dayData.day},${dayData.minutes}\n")
                }
                writer.append("\n")

                // Top Songs
                writer.append("TOP SONGS\n")
                writer.append("Rank,Title,Artist,Play Count\n")
                exportData.topSongs.forEach { song ->
                    writer.append("${song.rank},\"${song.title}\",\"${song.artist}\",${song.playCount}\n")
                }
                writer.append("\n")

                // Top Artists
                writer.append("TOP ARTISTS\n")
                writer.append("Rank,Artist,Play Count\n")
                exportData.topArtists.forEach { artist ->
                    writer.append("${artist.rank},\"${artist.name}\",${artist.playCount}\n")
                }
                writer.append("\n")

                // Day Streaks
                writer.append("DAY STREAKS\n")
                writer.append("Song,Artist,Start Date,End Date,Streak Days\n")
                exportData.analytics.dayStreaks.forEach { streak ->
                    writer.append("\"${streak.songTitle}\",\"${streak.artist}\",${streak.startDate},${streak.endDate},${streak.streakDays}\n")
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createExportFileApi29Plus(fileName: String): OutputStream? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Purritify")
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        return uri?.let { resolver.openOutputStream(it) }
    }



    suspend fun exportAllDataToCsv(allData: List<AnalyticsExportData>): Boolean {
        return try {
            val fileName = "purritify_analytics_all_data.csv"
            val file = createExportFile(fileName)

            FileWriter(file).use { writer ->
                // Header
                writer.append("Purritify Complete Analytics Export\n")
                writer.append("User,${allData.firstOrNull()?.userInfo?.username ?: "Unknown"}\n")
                writer.append("Export Date,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                writer.append("Total Months,${allData.size}\n\n")

                // Monthly Summary Table
                writer.append("MONTHLY SUMMARY\n")
                writer.append("Month,Total Minutes,Daily Avg,Songs Played,Artists,Top Artist,Top Song\n")
                allData.forEach { data ->
                    writer.append("${data.month},${data.analytics.totalMinutesListened},${data.analytics.dailyAverage},${data.analytics.totalSongsPlayed},${data.analytics.totalArtistsListened},\"${data.analytics.topArtist ?: "N/A"}\",\"${data.analytics.topSong ?: "N/A"}\"\n")
                }
                writer.append("\n")

                // Detailed data for each month
                allData.forEach { monthData ->
                    writer.append("=== ${monthData.month.uppercase()} DETAILED DATA ===\n")

                    // Top Songs for this month
                    writer.append("TOP SONGS - ${monthData.month}\n")
                    writer.append("Rank,Title,Artist,Play Count\n")
                    monthData.topSongs.take(10).forEach { song ->
                        writer.append("${song.rank},\"${song.title}\",\"${song.artist}\",${song.playCount}\n")
                    }
                    writer.append("\n")

                    // Top Artists for this month
                    writer.append("TOP ARTISTS - ${monthData.month}\n")
                    writer.append("Rank,Artist,Play Count\n")
                    monthData.topArtists.take(10).forEach { artist ->
                        writer.append("${artist.rank},\"${artist.name}\",${artist.playCount}\n")
                    }
                    writer.append("\n")

                    // Day Streaks for this month
                    if (monthData.analytics.dayStreaks.isNotEmpty()) {
                        writer.append("DAY STREAKS - ${monthData.month}\n")
                        writer.append("Song,Artist,Start Date,End Date,Streak Days\n")
                        monthData.analytics.dayStreaks.forEach { streak ->
                            writer.append("\"${streak.songTitle}\",\"${streak.artist}\",${streak.startDate},${streak.endDate},${streak.streakDays}\n")
                        }
                        writer.append("\n")
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportAllDataToPdfMediaStore(allData: List<AnalyticsExportData>): Boolean {
        return try {
            val resolver = context.contentResolver
            val fileName = "purritify_analytics_all_data_${System.currentTimeMillis()}.pdf"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Purritify")
            }

            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            if (uri == null) {
                Log.e("ExportPDF", "❌ Failed to create MediaStore entry.")
                return false
            }

            resolver.openOutputStream(uri)?.use { outputStream ->
                val document = PdfDocument()
                val paint = Paint().apply {
                    textSize = 12f
                    color = Color.BLACK
                }
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                var page = document.startPage(pageInfo)
                val canvas = page.canvas

                var yPos = 40
                fun drawLine(text: String) {
                    if (yPos > 800) {
                        document.finishPage(page)
                        page = document.startPage(pageInfo)
                        yPos = 40
                    }
                    canvas.drawText(text, 40f, yPos.toFloat(), paint)
                    yPos += 20
                }

                drawLine("Purritify Complete Analytics Export")
                drawLine("Export Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                drawLine("User: ${allData.firstOrNull()?.userInfo?.username ?: "Unknown"}")
                drawLine("Total Months: ${allData.size}")
                drawLine("")

                allData.forEach { data ->
                    drawLine("Month: ${data.month}")
                    drawLine("Total Minutes: ${data.analytics.totalMinutesListened}")
                    drawLine("Daily Avg: ${data.analytics.dailyAverage}")
                    drawLine("Songs Played: ${data.analytics.totalSongsPlayed}")
                    drawLine("Artists Listened: ${data.analytics.totalArtistsListened}")
                    drawLine("Top Artist: ${data.analytics.topArtist ?: "N/A"}")
                    drawLine("Top Song: ${data.analytics.topSong ?: "N/A"}")
                    drawLine("")

                    drawLine("Top Songs:")
                    data.topSongs.take(10).forEach {
                        drawLine("- ${it.rank}. ${it.title} by ${it.artist} (${it.playCount} plays)")
                    }
                    drawLine("")

                    drawLine("Top Artists:")
                    data.topArtists.take(10).forEach {
                        drawLine("- ${it.rank}. ${it.name} (${it.playCount} plays)")
                    }
                    drawLine("")

                    if (data.analytics.dayStreaks.isNotEmpty()) {
                        drawLine("Day Streaks:")
                        data.analytics.dayStreaks.forEach {
                            drawLine("- ${it.songTitle} by ${it.artist}, ${it.streakDays} days (${it.startDate} to ${it.endDate})")
                        }
                        drawLine("")
                    }
                }

                document.finishPage(page)
                document.writeTo(outputStream)
                document.close()

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Purritify/$fileName").absolutePath),
                    arrayOf("application/pdf"),
                    null
                )

            }

            Log.d("ExportPDF", "✅ PDF exported to MediaStore: $fileName")
            true
        } catch (e: Exception) {
            Log.e("ExportPDF", "❌ Failed to export PDF: ${e.message}", e)
            false
        }
    }


    private fun createExportFile(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val purritifyDir = File(downloadsDir, "Purritify")

        if (!purritifyDir.exists()) {
            val created = purritifyDir.mkdirs()
            Log.d("ExportCSV", "Creating directory ${purritifyDir.absolutePath} → success: $created")
        } else {
            Log.d("ExportCSV", "Directory already exists: ${purritifyDir.absolutePath}")
        }

        val outputFile = File(purritifyDir, fileName)
        Log.d("ExportCSV", "Export file path: ${outputFile.absolutePath}")
        return outputFile
    }

}