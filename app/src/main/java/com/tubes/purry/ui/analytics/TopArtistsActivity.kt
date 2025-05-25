package com.tubes.purry.ui.analytics

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tubes.purry.R
import com.tubes.purry.databinding.ActivityTopArtistsBinding
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TopArtistsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopArtistsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: TopArtistsAdapter
    private var currentMonth: String = ""

    private val viewModel: SoundCapsuleViewModel by viewModels {
        SoundCapsuleViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopArtistsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        sessionManager = SessionManager(this)
        currentMonth = intent.getStringExtra("month") ?: getCurrentMonth()

        setupUI()
        loadTopArtists()
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
        adapter = TopArtistsAdapter()
        binding.rvTopArtists.apply {
            adapter = this@TopArtistsActivity.adapter
            layoutManager = LinearLayoutManager(this@TopArtistsActivity)
        }
    }

    private fun loadTopArtists() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            showError("User not logged in")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val analytics = viewModel.getMonthlyAnalytics(userId, currentMonth)
                val topArtists = viewModel.getTopArtists(userId, currentMonth, 50)

                if (topArtists.isEmpty()) {
                    showNoData()
                } else {
                    // Update header
                    binding.tvSummary.text = "You listened to ${analytics.totalArtistsListened} artists this month.\n" + "Here are your Top 5 Artists"
                    // Update list
                    adapter.submitList(topArtists.take(5))
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
        binding.tvNoData.text = "No artists data available for ${formatMonthDisplay(currentMonth)}"
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