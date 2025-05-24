package com.tubes.purry.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubes.purry.data.model.*
import com.tubes.purry.data.repository.AnalyticsRepository
import com.tubes.purry.data.repository.ProfileRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SoundCapsuleViewModel(
    application: Application,
    private val analyticsRepository: AnalyticsRepository,
    private val profileRepository: ProfileRepository,
    private val exportService: AnalyticsExportService
) : AndroidViewModel(application) {

    suspend fun getMonthlyAnalytics(userId: Int, month: String): MonthlyAnalytics {
        return analyticsRepository.getMonthlyAnalytics(userId, month)
    }

    suspend fun getAllMonthsWithData(userId: Int): List<MonthlyAnalyticsSummary> {
        val currentDate = Calendar.getInstance()
        val months = mutableListOf<MonthlyAnalyticsSummary>()

        // Check last 12 months
        repeat(12) { i ->
            currentDate.add(Calendar.MONTH, if (i == 0) 0 else -1)
            val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentDate.time)

            val analytics = analyticsRepository.getMonthlyAnalytics(userId, monthStr)
            if (analytics.totalMinutesListened > 0) {
                months.add(
                    MonthlyAnalyticsSummary(
                        month = monthStr,
                        displayName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentDate.time),
                        totalMinutes = analytics.totalMinutesListened,
                        topArtist = analytics.topArtist,
                        topSong = analytics.topSong,
                        dayStreaks = analytics.dayStreaks.size
                    )
                )
            }
        }

        return months.sortedByDescending { it.month }
    }

    suspend fun getTopSongs(userId: Int, month: String, limit: Int = 50): List<TopSong> {
        return analyticsRepository.getTopSongs(userId, month, limit)
    }

    suspend fun getTopArtists(userId: Int, month: String, limit: Int = 50): List<TopArtist> {
        return analyticsRepository.getTopArtists(userId, month, limit)
    }

    suspend fun getDailyChart(userId: Int, month: String): List<DailyChartData> {
        return analyticsRepository.getDailyChart(userId, month)
    }

    suspend fun exportMonthData(userId: Int, month: String): Boolean {
        return try {
            val analytics = analyticsRepository.getMonthlyAnalytics(userId, month)
            val topSongs = analyticsRepository.getTopSongs(userId, month, 50)
            val topArtists = analyticsRepository.getTopArtists(userId, month, 50)
            val dailyChart = analyticsRepository.getDailyChart(userId, month)
            val profileResponse = profileRepository.getProfile()
            val profile = profileResponse.body()
                ?: throw Exception("Failed to retrieve profile")

            val exportData = AnalyticsExportData(
                month = month,
                userInfo = profile,
                analytics = analytics,
                topSongs = topSongs,
                topArtists = topArtists,
                dailyChart = dailyChart
            )

            exportService.exportToCsv(exportData)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportAllData(userId: Int): Boolean {
        return try {
            val months = getAllMonthsWithData(userId)
            val allExportData = mutableListOf<AnalyticsExportData>()

            for (monthSummary in months) {
                val analytics = analyticsRepository.getMonthlyAnalytics(userId, monthSummary.month)
                val topSongs = analyticsRepository.getTopSongs(userId, monthSummary.month, 50)
                val topArtists = analyticsRepository.getTopArtists(userId, monthSummary.month, 50)
                val dailyChart = analyticsRepository.getDailyChart(userId, monthSummary.month)
                val profileResponse = profileRepository.getProfile()
                val profile = profileResponse.body()
                    ?: throw Exception("Failed to retrieve profile")

                allExportData.add(
                    AnalyticsExportData(
                        month = monthSummary.month,
                        userInfo = profile,
                        analytics = analytics,
                        topSongs = topSongs,
                        topArtists = topArtists,
                        dailyChart = dailyChart
                    )
                )
            }

            exportService.exportAllDataToCsv(allExportData)
        } catch (e: Exception) {
            false
        }
    }

    fun startListeningSession(userId: Int, song: Song) {
        viewModelScope.launch {
            analyticsRepository.startListeningSession(userId, song)
        }
    }

    fun endListeningSession(sessionId: Long, duration: Long) {
        viewModelScope.launch {
            analyticsRepository.endListeningSession(sessionId, duration)
        }
    }
}

// Data class untuk summary list
data class MonthlyAnalyticsSummary(
    val month: String,
    val displayName: String,
    val totalMinutes: Long,
    val topArtist: String?,
    val topSong: String?,
    val dayStreaks: Int
)