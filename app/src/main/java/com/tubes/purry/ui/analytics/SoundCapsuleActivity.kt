package com.tubes.purry.ui.analytics

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.databinding.ActivitySoundCapsuleBinding
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SoundCapsuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySoundCapsuleBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: SoundCapsuleAdapter

    private val viewModel: SoundCapsuleViewModel by viewModels {
        SoundCapsuleViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoundCapsuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        sessionManager = SessionManager(this)
        setupUI()
        loadSoundCapsule()
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Setup RecyclerView
        adapter = SoundCapsuleAdapter(
            onMonthClick = { month -> openMonthDetail(month) },
            onExportClick = { month -> exportMonth(month) }
        )

        binding.rvSoundCapsule.apply {
            adapter = this@SoundCapsuleActivity.adapter
            layoutManager = LinearLayoutManager(this@SoundCapsuleActivity)
        }

        // Setup export all button
        binding.btnExportAll.setOnClickListener {
            exportAllData()
        }
    }

    private fun loadSoundCapsule() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            showError("User not logged in")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val months = viewModel.getAllMonthsWithData(userId)
                if (months.isEmpty()) {
                    showNoData()
                } else {
                    adapter.submitList(months)
                    binding.rvSoundCapsule.visibility = View.VISIBLE
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
        binding.rvSoundCapsule.visibility = View.GONE
        binding.tvNoData.visibility = View.VISIBLE
        binding.tvNoData.text = "No data available\nStart listening to music to see your Sound Capsule!"
    }

    private fun showError(message: String) {
        binding.rvSoundCapsule.visibility = View.GONE
        binding.tvNoData.visibility = View.VISIBLE
        binding.tvNoData.text = message
    }

    private fun openMonthDetail(month: String) {
        val intent = Intent(this, MonthDetailActivity::class.java)
        intent.putExtra("month", month)
        startActivity(intent)
    }

    private fun exportMonth(month: String) {
        val userId = sessionManager.getUserId() ?: return

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val success = viewModel.exportMonthData(userId, month)

                if (success) {
                    showToast("Export successful! Check your Downloads folder.")
                } else {
                    showToast("Export failed. Please try again.")
                }
            } catch (e: Exception) {
                showToast("Export error: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun exportAllData() {
        val userId = sessionManager.getUserId() ?: return

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val success = viewModel.exportAllData(userId)

                if (success) {
                    showToast("All data exported successfully!")
                } else {
                    showToast("Export failed. Please try again.")
                }
            } catch (e: Exception) {
                showToast("Export error: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showToast(message: String) {
        // You can implement Toast or Snackbar here
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}