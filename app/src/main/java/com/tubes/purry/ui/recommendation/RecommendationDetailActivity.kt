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
        val profileViewModelFactory = ProfileViewModelFactory(this)
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
        // Combine recently played, local new songs, and online trending songs
        songViewModel.recentlyPlayed.observe(this) { recentSongs ->
            songViewModel.newSongs.observe(this) { newSongs ->
                lifecycleScope.launch {
                    try {
                        // Get trending songs from API
                        val onlineSongs = ApiClient.apiService.getTopSongsGlobal()
                        val onlineTemporarySongs = onlineSongs.take(10).map { it.toTemporarySong() }

                        // Mix local and online songs
                        val mixedSongs = (recentSongs + newSongs + onlineTemporarySongs)
                            .distinctBy { it.id }
                            .shuffled()
                            .take(20)

                        currentSongs = mixedSongs
                        adapter.submitList(mixedSongs)
                    } catch (e: Exception) {
                        Log.e("RecommendationDetail", "Error loading online songs for daily mix", e)
                        // Fallback to local songs only
                        val localMixedSongs = (recentSongs + newSongs)
                            .distinctBy { it.id }
                            .shuffled()
                            .take(20)
                        currentSongs = localMixedSongs
                        adapter.submitList(localMixedSongs)
                        Toast.makeText(this@RecommendationDetailActivity,
                            "Using offline songs only", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadRecentlyPlayedMix() {
        songViewModel.recentlyPlayed.observe(this) { songs ->
            lifecycleScope.launch {
                try {
                    // Add some trending songs to recently played mix
                    val onlineSongs = ApiClient.apiService.getTopSongsGlobal()
                    val onlineTemporarySongs = onlineSongs.take(5).map { it.toTemporarySong() }

                    val mixedSongs = (songs + onlineTemporarySongs).shuffled().take(15)
                    currentSongs = mixedSongs
                    adapter.submitList(mixedSongs)
                } catch (e: Exception) {
                    Log.e("RecommendationDetail", "Error loading online songs for recently played", e)
                    // Fallback to local songs only
                    val shuffledSongs = songs.shuffled().take(15)
                    currentSongs = shuffledSongs
                    adapter.submitList(shuffledSongs)
                }
            }
        }
    }

    private fun loadLikedSongsMix() {
        // Get current user ID
        val db = AppDatabase.getDatabase(applicationContext)
        val userId = sessionManager.getUserId()

        if (userId != null) {
            db.LikedSongDao().getLikedSongsByUser(userId).asLiveData().observe(this) { likedSongs ->
                getUserLocationFromProfile { userLocation ->
                    lifecycleScope.launch {
                        try {
                            val countryCode = getCountryCodeFromLocation(userLocation)

                            // Get trending songs from user's country
                            val onlineSongs = if (countryCode.isNotEmpty()) {
                                ApiClient.apiService.getTopSongsByCountry(countryCode)
                            } else {
                                ApiClient.apiService.getTopSongsGlobal()
                            }

                            val onlineTemporarySongs = onlineSongs.take(10).map { it.toTemporarySong() }

                            // Mix liked songs with trending songs from user's region
                            val mixedSongs = (likedSongs + onlineTemporarySongs).shuffled().take(25)
                            currentSongs = mixedSongs
                            adapter.submitList(mixedSongs)
                        } catch (e: Exception) {
                            Log.e("RecommendationDetail", "Error loading online songs for liked mix", e)
                            // Fallback to liked songs only
                            val shuffledSongs = likedSongs.shuffled().take(25)
                            currentSongs = shuffledSongs
                            adapter.submitList(shuffledSongs)
                        }
                    }
                }
            }
        }
    }

    private fun loadDiscoveryMix() {
        // Mix of local new songs and trending online songs for discovery
        songViewModel.newSongs.observe(this) { localNewSongs ->
            getUserLocationFromProfile { userLocation ->
                lifecycleScope.launch {
                    try {
                        val countryCode = getCountryCodeFromLocation(userLocation)

                        // Get trending songs (prefer country-specific for better discovery)
                        val onlineSongs = if (countryCode.isNotEmpty()) {
                            // Mix both country-specific and global for better discovery
                            val countrySongs = ApiClient.apiService.getTopSongsByCountry(countryCode)
                            val globalSongs = ApiClient.apiService.getTopSongsGlobal()
                            (countrySongs + globalSongs).distinctBy { it.id }
                        } else {
                            ApiClient.apiService.getTopSongsGlobal()
                        }

                        val onlineTemporarySongs = onlineSongs.map { it.toTemporarySong() }

                        // Prioritize online songs for discovery, mix with some local
                        val discoveryMix = (onlineTemporarySongs + localNewSongs)
                            .distinctBy { it.id }
                            .shuffled()
                            .take(30)

                        currentSongs = discoveryMix
                        adapter.submitList(discoveryMix)
                    } catch (e: Exception) {
                        Log.e("RecommendationDetail", "Error loading online songs for discovery", e)
                        // Fallback to local songs only
                        val discoveryMix = localNewSongs.shuffled().take(30)
                        currentSongs = discoveryMix
                        adapter.submitList(discoveryMix)
                        Toast.makeText(this@RecommendationDetailActivity,
                            "Discovery mix using offline songs", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getUserLocationFromProfile(callback: (String) -> Unit) {
        profileViewModel.profileData.observe(this) { profileData ->
            val location = profileData?.location ?: "US" // Default to US
            callback(location)
        }
    }

    private fun getCountryCodeFromLocation(location: String): String {
        // Simple mapping of common locations to country codes
        // You might want to use a more sophisticated location-to-country mapping
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
            else -> "" // Return empty for global trending
        }
    }

    private fun onSongClicked(song: Song) {
        Log.d("RecommendationDetail", "Song clicked: ${song.title}")
        nowPlayingViewModel.setQueueFromClickedSong(song, currentSongs, this)
        nowPlayingViewModel.playSong(song, this)

        // Only mark local songs as played
        if (song.isLocal) {
            songViewModel.markAsPlayed(song)
        }

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