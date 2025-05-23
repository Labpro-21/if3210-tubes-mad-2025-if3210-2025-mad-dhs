package com.tubes.purry.ui.chart

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.toTemporarySong
import com.tubes.purry.databinding.ActivityTopChartDetailBinding
import com.tubes.purry.ui.player.MiniPlayerFragment
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory

class TopChartDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopChartDetailBinding
    private lateinit var adapter: OnlineSongListAdapter
    private lateinit var chartViewModel: ChartViewModel
    private lateinit var nowPlayingViewModel: NowPlayingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopChartDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appContext = applicationContext
        val db = AppDatabase.getDatabase(appContext)
        val likedSongDao = db.LikedSongDao()
        val songDao = db.songDao()
        val profileViewModel = ViewModelProvider(this, ProfileViewModelFactory(appContext))[ProfileViewModel::class.java]
        val nowPlayingFactory = NowPlayingViewModelFactory(
            requireNotNull(application),
            likedSongDao,
            songDao,
            profileViewModel
        )
        nowPlayingViewModel = ViewModelProvider(this, nowPlayingFactory)[NowPlayingViewModel::class.java]
        chartViewModel = ViewModelProvider(this, ChartViewModelFactory())[ChartViewModel::class.java]

        // === UI SETUP ===
        val isGlobal = intent.getBooleanExtra("isGlobal", true)
        val chartTitle = if (isGlobal) "Top 50 GLOBAL" else "Top 50 INDONESIA"
        val coverRes = if (isGlobal) R.drawable.cov_top50_global else R.drawable.cov_top50_id

        binding.tvChartTitle.text = chartTitle
        binding.tvChartDescription.text = "Your daily update of the most played tracks right now - $chartTitle"
        Glide.with(this).load(coverRes).into(binding.ivChartCover)

        // === ADAPTER SETUP ===
        adapter = OnlineSongListAdapter(
            songs = emptyList(),
            onClick = { onlineSong ->
                val tempSong = onlineSong.toTemporarySong()
                Log.d("TopChartDetail", "Calling playSong for: ${tempSong.title}")

                nowPlayingViewModel.setQueueFromClickedSong(tempSong, listOf(tempSong), this)
                nowPlayingViewModel.playSong(tempSong, this)

                val container = findViewById<FrameLayout>(R.id.miniPlayerContainer)
                if (container != null) {
                    // Cek apakah MiniPlayerFragment sudah ditambahkan
                    val existingFragment = supportFragmentManager.findFragmentById(R.id.miniPlayerContainer)
                    if (existingFragment == null) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
                            .commit()
                    }

                    container.alpha = 0f
                    container.visibility = FrameLayout.VISIBLE
                    container.animate().alpha(1f).setDuration(250).start()
                } else {
                    Toast.makeText(this, "miniPlayerContainer not found in layout", Toast.LENGTH_SHORT).show()
                }
            },
            onDownloadClick = { song ->
                Toast.makeText(this, "Download: ${song.title}", Toast.LENGTH_SHORT).show()
                // TODO: implementasi download
            }
        )

        binding.rvChartSongs.layoutManager = LinearLayoutManager(this)
        binding.rvChartSongs.adapter = adapter

        // === OBSERVER SETUP ===
        chartViewModel.songs.observe(this) { songs ->
            adapter.updateSongs(songs)
        }

        chartViewModel.error.observe(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }

        // === FETCH SONGS ===
        val countryCode = if (!isGlobal) "ID" else null
        chartViewModel.fetchSongs(isGlobal, countryCode)
    }

    private fun showMiniPlayer() {
        showMiniPlayer()
    }
}
