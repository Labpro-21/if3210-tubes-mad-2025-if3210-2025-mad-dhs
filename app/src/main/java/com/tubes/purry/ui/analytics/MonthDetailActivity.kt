package com.tubes.purry.ui.analytics

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.databinding.ActivityMonthDetailBinding
import com.tubes.purry.data.model.MonthlyAnalytics
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MonthDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonthDetailBinding
    private lateinit var sessionManager: SessionManager
    private var currentMonth: String = ""

    private val viewModel: SoundCapsuleViewModel by viewModels {
        SoundCapsuleViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        currentMonth = intent.getStringExtra("month") ?: getCurrentMonth()

        setupUI()
        loadMonthData()
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Set month title
        val monthName = formatMonthDisplay(currentMonth)
        binding.tvMonthTitle.text = monthName

        // Setup click listeners
        binding.cardTimeListened.setOnClickListener {
            openTimeListenedDetail()
        }

        binding.cardTopArtists.setOnClickListener {
            openTopArtistsDetail()
        }

        binding.cardTopSongs.setOnClickListener {
            openTopSongsDetail()
        }

        binding.btnExport.setOnClickListener {
            exportCurrentMonth()
        }
    }

    private fun loadMonthData() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            showError("User not logged in")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val analytics = viewModel.getMonthlyAnalytics(userId, currentMonth)
                if (analytics.totalMinutesListened == 0L) {
                    showNoData()
                } else {
                    displayAnalytics(analytics)
                    binding.contentLayout.visibility = View.VISIBLE
                    binding.tvNoData.visibility = View.GONE
                }
            } catch (e: Exception) {
                showError("Failed to load data: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun displayAnalytics(analytics: MonthlyAnalytics) {
        // Time listened
        binding.tvTotalMinutes.text = "${analytics.totalMinutesListened} minutes"
        binding.tvDailyAverage.text = "Daily average: ${analytics.dailyAverage} min"

        // Top artist
        if (analytics.topArtist != null) {
            binding.tvTopArtist.text = analytics.topArtist
            binding.tvTopArtistPlays.text = "${analytics.topArtistPlayCount} plays"

            if (analytics.topArtistCover != null) {
                Glide.with(this)
                    .load(analytics.topArtistCover)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(binding.ivTopArtistCover)
            }
        } else {
            binding.tvTopArtist.text = "No data"
            binding.tvTopArtistPlays.text = ""
        }

        // Top song
        if (analytics.topSong != null) {
            binding.tvTopSong.text = analytics.topSong
            binding.tvTopSongArtist.text = analytics.topSongArtist
            binding.tvTopSongPlays.text = "${analytics.topSongPlayCount} plays"

            if (analytics.topSongCover != null) {
                Glide.with(this)
                    .load(analytics.topSongCover)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(binding.ivTopSongCover)
            }
        } else {
            binding.tvTopSong.text = "No data"
            binding.tvTopSongArtist.text = ""
            binding.tvTopSongPlays.text = ""
        }

        // Stats
        binding.tvTotalSongs.text = "${analytics.totalSongsPlayed} different songs"
        binding.tvTotalArtists.text = "${analytics.totalArtistsListened} artists"

        // Day streaks
        if (analytics.dayStreaks.isNotEmpty()) {
            val topStreak = analytics.dayStreaks.first()
            binding.tvStreakSong.text = topStreak.songTitle
            binding.tvStreakArtist.text = topStreak.artist
            binding.tvStreakDays.text = "${topStreak.streakDays}-day streak"
            binding.tvStreakDates.text = "${topStreak.startDate} - ${topStreak.endDate}"

            if (topStreak.cover != null) {
                Glide.with(this)
                    .load(topStreak.cover)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(binding.ivStreakCover)
            }

            binding.cardDayStreak.visibility = View.VISIBLE
        } else {
            binding.cardDayStreak.visibility = View.GONE
        }
    }

    private fun showNoData() {
        binding.contentLayout.visibility = View.GONE
        binding.tvNoData.visibility = View.VISIBLE
        binding.tvNoData.text = "No data available for ${formatMonthDisplay(currentMonth)}"
    }

    private fun showError(message: String) {
        binding.contentLayout.visibility = View.GONE
        binding.tvNoData.visibility = View.VISIBLE
        binding.tvNoData.text = message
    }

    private fun openTimeListenedDetail() {
        val intent = Intent(this, TimeListenedDetailActivity::class.java)
        intent.putExtra("month", currentMonth)
        startActivity(intent)
    }

    private fun openTopArtistsDetail() {
        val intent = Intent(this, TopArtistsActivity::class.java)
        intent.putExtra("month", currentMonth)
        startActivity(intent)
    }

    private fun openTopSongsDetail() {
        val intent = Intent(this, TopSongsActivity::class.java)
        intent.putExtra("month", currentMonth)
        startActivity(intent)
    }

    private fun exportCurrentMonth() {
        val userId = sessionManager.getUserId() ?: return

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val success = viewModel.exportMonthData(userId, currentMonth)

                if (success) {
                    showToast("Export successful!")
                } else {
                    showToast("Export failed.")
                }
            } catch (e: Exception) {
                showToast("Export error: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun formatMonthDisplay(month: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(month)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            month
        }
    }

    private fun getCurrentMonth(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}