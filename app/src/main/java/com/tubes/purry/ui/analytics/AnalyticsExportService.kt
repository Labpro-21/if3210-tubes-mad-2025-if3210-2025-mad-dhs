package com.tubes.purry.ui.analytics

import android.content.Context
import android.os.Environment
import com.tubes.purry.data.model.AnalyticsExportData
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsExportService(private val context: Context) {

    suspend fun exportToCsv(exportData: AnalyticsExportData): Boolean {
        return try {
            val fileName = "purritify_analytics_${exportData.month}.csv"
            val file = createExportFile(fileName)

            FileWriter(file).use { writer ->
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

    private fun createExportFile(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val purritifyDir = File(downloadsDir, "Purritify")

        if (!purritifyDir.exists()) {
            purritifyDir.mkdirs()
        }

        return File(purritifyDir, fileName)
    }
}