package com.tubes.purry.ui.recommendation

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.RecommendationType
import com.tubes.purry.data.model.Song
import com.tubes.purry.data.model.toTemporarySong
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.databinding.ActivityRecommendationDetailBinding
import com.tubes.purry.ui.library.SongListAdapter
import com.tubes.purry.ui.library.SongViewModel
import com.tubes.purry.ui.library.SongViewModelFactory
import com.tubes.purry.ui.player.MiniPlayerFragment
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.utils.SessionManager
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import kotlinx.coroutines.launch

class RecommendationDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecommendationDetailBinding
    private lateinit var adapter: SongListAdapter
    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var profileViewModel: ProfileViewModel
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
        setupViewModels()
        setupUI()
        loadRecommendations()
    }

    private fun setupViewModels() {
        val db = AppDatabase.getDatabase(applicationContext)
        val likedSongDao = db.LikedSongDao()
        val songDao = db.songDao()

        // Create ProfileViewModel using its proper factory
        val profileViewModelFactory = ProfileViewModelFactory(application)
        profileViewModel = ViewModelProvider(this, profileViewModelFactory)[ProfileViewModel::class.java]

        // Create NowPlayingViewModel with ProfileViewModel
        val factory = NowPlayingViewModelFactory(application, likedSongDao, songDao, profileViewModel)
        nowPlayingViewModel = ViewModelProvider(this, factory)[NowPlayingViewModel::class.java]
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Setup adapter
        adapter = SongListAdapter(
            onClick = { song -> onSongClicked(song) },
            onEdit = { /* Not needed for recommendations */ },
            onDelete = { /* Not needed for recommendations */ },
            showEditDelete = false
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
        val imageRes = intent.getIntExtra("image_res", R.drawable.cov_top50_global)

        // Set UI
        binding.ivRecommendationCover.setImageResource(imageRes)
        binding.tvRecommendationTitle.text = title
        binding.tvRecommendationDescription.text = description

        // Load songs based on type
        when (recommendationType) {
            RecommendationType.DAILY_MIX -> loadDailyMix()
            RecommendationType.RECENTLY_PLAYED_MIX -> loadRecentlyPlayedMix()
            RecommendationType.LIKED_SONGS_MIX -> loadLikedSongsMix()
            RecommendationType.DISCOVERY_MIX -> loadDiscoveryMix()
        }
    }

    private fun loadDailyMix() {
        currentSongs = emptyList()
        adapter.submitList(emptyList())
        Toast.makeText(this, "Daily mix is not available", Toast.LENGTH_SHORT).show()
    }

    private fun loadRecentlyPlayedMix() {
        songViewModel.recentlyPlayed.observe(this) { localRecentSongs ->
            lifecycleScope.launch {
                try {
                    val recentSongs = localRecentSongs.take(20)
                    currentSongs = recentSongs
                    adapter.submitList(recentSongs)

                    Log.d("RecommendationDetail", "Loaded ${recentSongs.size} recently played songs")
                } catch (e: Exception) {
                    Log.e("RecommendationDetail", "Error loading recently played songs", e)
                    currentSongs = localRecentSongs
                    adapter.submitList(localRecentSongs)
                }
            }
        }
    }

    private fun loadLikedSongsMix() {
        val db = AppDatabase.getDatabase(applicationContext)
        val userId = sessionManager.getUserId()

        if (userId != null) {
            db.LikedSongDao().getLikedSongsByUser(userId).asLiveData().observe(this) { likedSongs ->
                lifecycleScope.launch {
                    try {
                        val onlineSongs = ApiClient.apiService.getTopSongsGlobal()

                        val similarOnlineSongs = mutableListOf<Song>()

                        for (likedSong in likedSongs) {
                            val matchingSongs = onlineSongs.filter { onlineSong ->
                                val titleMatch = onlineSong.title.contains(likedSong.title, ignoreCase = true) ||
                                        likedSong.title.contains(onlineSong.title, ignoreCase = true)
                                val artistMatch = onlineSong.artist.contains(likedSong.artist, ignoreCase = true) ||
                                        likedSong.artist.contains(onlineSong.artist, ignoreCase = true)
                                titleMatch || artistMatch
                            }

                            similarOnlineSongs.addAll(matchingSongs.map { it.toTemporarySong() })
                        }

                        val uniqueSimilarSongs = similarOnlineSongs.distinctBy { it.id }
                        val mixedSongs = (likedSongs + uniqueSimilarSongs).distinctBy {
                            "${it.title.lowercase()}-${it.artist.lowercase()}"
                        }.take(25)

                        currentSongs = mixedSongs
                        adapter.submitList(mixedSongs)

                        Log.d("RecommendationDetail",
                            "Loaded ${likedSongs.size} liked songs + ${uniqueSimilarSongs.size} similar online songs")

                    } catch (e: Exception) {
                        Log.e("RecommendationDetail", "Error loading similar online songs", e)
                        currentSongs = likedSongs
                        adapter.submitList(likedSongs)
                    }
                }
            }
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
                        val randomOnlineSongs = onlineSongs.shuffled().take(15)
                            .map { it.toTemporarySong() }

                        val discoveryMix = (unplayedLocalSongs + randomOnlineSongs)
                            .distinctBy { it.id }
                            .shuffled()
                            .take(30)

                        currentSongs = discoveryMix
                        adapter.submitList(discoveryMix)

                        Log.d("RecommendationDetail",
                            "Discovery mix: ${unplayedLocalSongs.size} unplayed local + ${randomOnlineSongs.size} random online")

                    } catch (e: Exception) {
                        Log.e("RecommendationDetail", "Error loading discovery mix", e)
                        val recentlyPlayedIds = recentlyPlayedSongs.map { it.id }.toSet()
                        val unplayedLocalSongs = allLocalSongs.filter {
                            it.id !in recentlyPlayedIds
                        }.shuffled().take(30)

                        currentSongs = unplayedLocalSongs
                        adapter.submitList(unplayedLocalSongs)
                        Toast.makeText(this@RecommendationDetailActivity,
                            "Discovery mix using unplayed local songs only", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getCountryCodeFromLocation(location: String): String {
        return when {
            location.contains("Indonesia", ignoreCase = true) -> "ID"
            location.contains("United States", ignoreCase = true) ||
                    location.contains("USA", ignoreCase = true) -> "US"
            location.contains("United Kingdom", ignoreCase = true) ||
                    location.contains("UK", ignoreCase = true) -> "GB"
            location.contains("Canada", ignoreCase = true) -> "CA"
            location.contains("Australia", ignoreCase = true) -> "AU"
            location.contains("Germany", ignoreCase = true) -> "DE"
            location.contains("France", ignoreCase = true) -> "FR"
            location.contains("Japan", ignoreCase = true) -> "JP"
            location.contains("Korea", ignoreCase = true) -> "KR"
            location.contains("Brazil", ignoreCase = true) -> "BR"
            location.contains("India", ignoreCase = true) -> "IN"
            location.contains("China", ignoreCase = true) -> "CN"
            else -> ""
        }
    }

    private fun onSongClicked(song: Song) {
        Log.d("RecommendationDetail", "Song clicked: ${song.title}")
        nowPlayingViewModel.setQueueFromClickedSong(song, currentSongs, this)
        nowPlayingViewModel.playSong(song, this)

//        // Only mark local songs as played
//        if (song.isLocal) {
//            songViewModel.markAsPlayed(song)
//        }
        songViewModel.markAsPlayed(song)

        // Show mini player
        val fragmentManager = supportFragmentManager
        val existingFragment = fragmentManager.findFragmentById(R.id.miniPlayerContainer)
        if (existingFragment == null) {
            fragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
                .commit()
        }

        val container = findViewById<FrameLayout>(R.id.miniPlayerContainer)
        if (container.visibility != View.VISIBLE) {
            container.alpha = 0f
            container.visibility = View.VISIBLE
            container.animate().alpha(1f).setDuration(250).start()
        }
    }

    private fun getUserId(): Int? {
        return sessionManager.getUserId()
    }
}