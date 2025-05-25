package com.tubes.purry.ui.analytics

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tubes.purry.databinding.ActivityTopSongsBinding
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TopSongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopSongsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: TopSongsAdapter
    private var currentMonth: String = ""

    private val viewModel: SoundCapsuleViewModel by viewModels {
        SoundCapsuleViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopSongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        currentMonth = intent.getStringExtra("month") ?: getCurrentMonth()

        setupUI()
        loadTopSongs()
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
        binding.tvMonth.text = monthName

        // Setup RecyclerView
        adapter = TopSongsAdapter()
        binding.rvTopSongs.apply {
            adapter = this@TopSongsActivity.adapter
            layoutManager = LinearLayoutManager(this@TopSongsActivity)
        }
    }

    private fun loadTopSongs() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            showError("User not logged in")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val analytics = viewModel.getMonthlyAnalytics(userId, currentMonth)
                val topSongs = viewModel.getTopSongs(userId, currentMonth, 50)

                if (topSongs.isEmpty()) {
                    showNoData()
                } else {
                    // Update header
                    binding.tvSummary.text = "You played ${analytics.totalSongsPlayed} different songs this month."

                    // Update list
                    adapter.submitList(topSongs)
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

    private fun showNoData() {
        binding.contentLayout.visibility = View.GONE
        binding.tvNoData.visibility = View.VISIBLE
        binding.tvNoData.text = "No songs data available for ${formatMonthDisplay(currentMonth)}"
    }

    private fun showError(message: String) {
        binding.contentLayout.visibility = View.GONE
        binding.tvNoData.visibility = View.VISIBLE
        binding.tvNoData.text = message
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
}