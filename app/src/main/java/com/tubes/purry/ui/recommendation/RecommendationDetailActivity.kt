package com.tubes.purry.ui.recommendation

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tubes.purry.PurrytifyApplication
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.RecommendationType
import com.tubes.purry.data.model.Song
import com.tubes.purry.data.model.toTemporarySong
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.databinding.ActivityRecommendationDetailBinding
import com.tubes.purry.ui.library.SongViewModel
import com.tubes.purry.ui.library.SongViewModelFactory
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.launch

class RecommendationDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecommendationDetailBinding
    private lateinit var adapter: RecommendationSongAdapter
    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private var currentSongs: List<Song> = emptyList()
    private lateinit var sessionManager: SessionManager

    private val songViewModel: SongViewModel by viewModels {
        SongViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecommendationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        nowPlayingViewModel = (application as PurrytifyApplication).nowPlayingViewModel

        setupUI()
        loadRecommendations()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        adapter = RecommendationSongAdapter(
            songs = emptyList(),
            context = this,
            onClick = { song -> onSongClicked(song) },
            country = "ID" // Bisa diganti dengan dari profile jika perlu
        )

        binding.rvRecommendationSongs.apply {
            adapter = this@RecommendationDetailActivity.adapter
            layoutManager = LinearLayoutManager(this@RecommendationDetailActivity)
        }
    }

    private fun loadRecommendations() {
        val recommendationType = RecommendationType.valueOf(
            intent.getStringExtra("recommendation_type") ?: RecommendationType.DAILY_MIX.name
        )
        val title = intent.getStringExtra("title") ?: "Recommendation"
        val description = intent.getStringExtra("description") ?: ""
        val imageRes = intent.getIntExtra("image_res", R.drawable.cov_playlist_global)

        binding.ivRecommendationCover.setImageResource(imageRes)
        binding.tvRecommendationTitle.text = title
        binding.tvRecommendationDescription.text = description

        when (recommendationType) {
            RecommendationType.DAILY_MIX -> loadDailyMix()
            RecommendationType.RECENTLY_PLAYED_MIX -> loadRecentlyPlayedMix()
            RecommendationType.LIKED_SONGS_MIX -> loadLikedSongsMix()
            RecommendationType.DISCOVERY_MIX -> loadDiscoveryMix()
        }
    }

    private fun loadDailyMix() {
        lifecycleScope.launch {
            try {
                val onlineSongs = ApiClient.apiService.getTopSongsGlobal()
                val dailyMixSongs = onlineSongs.shuffled().take(25).map { it.toTemporarySong() }

                currentSongs = dailyMixSongs
                adapter.updateSongs(dailyMixSongs)
                adapter.checkDownloadedStatus()

                Log.d("RecommendationDetail", "Daily mix loaded: ${dailyMixSongs.size} songs")
            } catch (e: Exception) {
                Log.e("RecommendationDetail", "Error loading daily mix", e)
                Toast.makeText(this@RecommendationDetailActivity, "Failed to load daily mix", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRecentlyPlayedMix() {
        songViewModel.recentlyPlayed.observe(this) { localRecentSongs ->
            lifecycleScope.launch {
                try {
                    val recentSongs = localRecentSongs.take(20)
                    val onlineSongs = ApiClient.apiService.getTopSongsGlobal()
                    val additionalSongs = onlineSongs.shuffled().take(10).map { it.toTemporarySong() }

                    val mixedSongs = (recentSongs + additionalSongs).distinctBy { it.id }
                    currentSongs = mixedSongs
                    adapter.updateSongs(mixedSongs)
                    adapter.checkDownloadedStatus()

                    Log.d("RecommendationDetail", "Recently played mix loaded: ${mixedSongs.size} songs")
                } catch (e: Exception) {
                    Log.e("RecommendationDetail", "Error loading recently played songs", e)
                    currentSongs = localRecentSongs
                    adapter.updateSongs(localRecentSongs)
                    adapter.checkDownloadedStatus()
                }
            }
        }
    }

    private fun loadLikedSongsMix() {
        val db = AppDatabase.getDatabase(applicationContext)
        val userId = sessionManager.getUserId()

        if (userId != null) {
            db.LikedSongDao().getLikedSongsByUser(userId).observe(this) { likedSongs ->
                lifecycleScope.launch {
                    try {
                        val onlineSongs = ApiClient.apiService.getTopSongsGlobal()

                        val similarOnlineSongs = likedSongs.flatMap { likedSong ->
                            onlineSongs.filter { onlineSong ->
                                onlineSong.title.contains(likedSong.title, true) ||
                                        likedSong.title.contains(onlineSong.title, true) ||
                                        onlineSong.artist.contains(likedSong.artist, true) ||
                                        likedSong.artist.contains(onlineSong.artist, true)
                            }.map { it.toTemporarySong() }
                        }

                        val uniqueSimilarSongs = similarOnlineSongs.distinctBy { it.id }
                        val mixedSongs = (likedSongs + uniqueSimilarSongs).distinctBy {
                            "${it.title.lowercase()}-${it.artist.lowercase()}"
                        }.take(25)

                        currentSongs = mixedSongs
                        adapter.updateSongs(mixedSongs)
                        adapter.checkDownloadedStatus()

                        Log.d("RecommendationDetail", "Liked songs mix loaded: ${mixedSongs.size} songs")

                    } catch (e: Exception) {
                        Log.e("RecommendationDetail", "Error loading similar online songs", e)
                        currentSongs = likedSongs
                        adapter.updateSongs(likedSongs)
                        adapter.checkDownloadedStatus()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Please login to see liked songs mix", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDiscoveryMix() {
        songViewModel.allSongs.observe(this) { allLocalSongs ->
            songViewModel.recentlyPlayed.observe(this) { recentlyPlayedSongs ->
                lifecycleScope.launch {
                    try {
                        val recentlyPlayedIds = recentlyPlayedSongs.map { it.id }.toSet()
                        val unplayedLocalSongs = allLocalSongs.filter {
                            it.id !in recentlyPlayedIds
                        }.shuffled().take(15)

                        val onlineSongs = ApiClient.apiService.getTopSongsGlobal()
                        val randomOnlineSongs = onlineSongs.shuffled().take(15).map { it.toTemporarySong() }

                        val discoveryMix = (unplayedLocalSongs + randomOnlineSongs)
                            .distinctBy { it.id }
                            .shuffled()
                            .take(30)

                        currentSongs = discoveryMix
                        adapter.updateSongs(discoveryMix)
                        adapter.checkDownloadedStatus()

                        Log.d("RecommendationDetail", "Discovery mix loaded: ${discoveryMix.size} songs")

                    } catch (e: Exception) {
                        Log.e("RecommendationDetail", "Error loading discovery mix", e)
                        val recentlyPlayedIds = recentlyPlayedSongs.map { it.id }.toSet()
                        val fallbackSongs = allLocalSongs.filter {
                            it.id !in recentlyPlayedIds
                        }.shuffled().take(30)

                        currentSongs = fallbackSongs
                        adapter.updateSongs(fallbackSongs)
                        adapter.checkDownloadedStatus()
                        Toast.makeText(this@RecommendationDetailActivity, "Discovery mix using local songs only", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun onSongClicked(song: Song) {
        Log.d("RecommendationDetail", "Song clicked: ${song.title}")
        Log.d("RecommendationDetail", "Song ID: ${song.id}")
        Log.d("RecommendationDetail", "Song isLocal: ${song.isLocal}")
        Log.d("RecommendationDetail", "Song serverId: ${song.serverId}")
        Log.d("RecommendationDetail", "Current songs count: ${currentSongs.size}")

        if (currentSongs.isNotEmpty()) {
            try {
                nowPlayingViewModel.setQueueFromClickedSong(song, currentSongs, this)
                nowPlayingViewModel.playSong(song, this)

                if (song.isLocal) {
                    songViewModel.markAsPlayed(song)
                }

                Log.d("RecommendationDetail", "Song playback initiated successfully")
                Toast.makeText(this, "Playing: ${song.title}", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("RecommendationDetail", "Error playing song", e)
                Toast.makeText(this, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("RecommendationDetail", "No songs in current queue")
            Toast.makeText(this, "No songs available to play", Toast.LENGTH_SHORT).show()
        }
    }
}
