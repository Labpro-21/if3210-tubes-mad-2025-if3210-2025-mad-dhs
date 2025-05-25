package com.tubes.purry.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.tubes.purry.databinding.ActivityTimeListenedDetailBinding
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TimeListenedDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeListenedDetailBinding
    private lateinit var sessionManager: SessionManager
    private var currentMonth: String = ""

    private val viewModel: SoundCapsuleViewModel by viewModels {
        SoundCapsuleViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeListenedDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        currentMonth = intent.getStringExtra("month") ?: getCurrentMonth()

        setupUI()
        loadTimeData()
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

        setupChart()
    }

    private fun setupChart() {
        binding.dailyChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // Styling
            setBackgroundColor(Color.TRANSPARENT)

            // X-axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.WHITE
                textSize = 12f
            }

            // Y-axis
            axisLeft.apply {
                textColor = Color.WHITE
                setDrawGridLines(true)
                gridColor = Color.GRAY
            }

            axisRight.isEnabled = false

            // Legend
            legend.apply {
                textColor = Color.WHITE
                textSize = 14f
            }
        }
    }

    private fun loadTimeData() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            showError("User not logged in")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val analytics = viewModel.getMonthlyAnalytics(userId, currentMonth)
                val dailyChart = viewModel.getDailyChart(userId, currentMonth)

                if (analytics.totalMinutesListened == 0L) {
                    showNoData()
                } else {
                    displayTimeData(analytics, dailyChart)
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

    private fun displayTimeData(analytics: com.tubes.purry.data.model.MonthlyAnalytics, dailyChart: List<com.tubes.purry.data.model.DailyChartData>) {
        // Display summary
        binding.tvTotalMinutes.text = "${analytics.totalMinutesListened} minutes"
        binding.tvDailyAverage.text = "Daily average: ${analytics.dailyAverage} min"

        // Setup chart data
        if (dailyChart.isNotEmpty()) {
            val entries = dailyChart.map { Entry(it.day.toFloat(), it.minutes.toFloat()) }
            val dayLabels = dailyChart.map { it.day.toString() }

            val dataSet = LineDataSet(entries, "Minutes").apply {
                color = Color.parseColor("#1DB954") // Spotify green
                setCircleColor(Color.parseColor("#1DB954"))
                lineWidth = 3f
                circleRadius = 5f
                setDrawCircleHole(false)
                valueTextColor = Color.WHITE
                valueTextSize = 10f
                setDrawValues(false) // Hide values on points for cleaner look
            }

            val lineData = LineData(dataSet)
            binding.dailyChart.apply {
                data = lineData
                xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
                xAxis.granularity = 1f
                xAxis.labelCount = minOf(dayLabels.size, 10) // Show max 10 labels

                // Animate
                animateX(1000)
                invalidate()
            }

            binding.dailyChart.visibility = View.VISIBLE
            binding.tvChartTitle.visibility = View.VISIBLE
        } else {
            binding.dailyChart.visibility = View.GONE
            binding.tvChartTitle.visibility = View.GONE
        }
    }

    private fun showNoData() {
        binding.contentLayout.visibility = View.GONE
        binding.tvNoData.visibility = View.VISIBLE
        binding.tvNoData.text = "No listening data available for ${formatMonthDisplay(currentMonth)}"
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