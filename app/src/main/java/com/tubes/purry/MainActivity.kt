package com.tubes.purry

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.ActivityMainBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.player.PlayerController
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.TokenExpirationService
import com.tubes.purry.utils.seedAssets

class MainActivity : AppCompatActivity(), NetworkStateReceiver.NetworkStateListener {

    private lateinit var binding: ActivityMainBinding
    private val networkStateReceiver = NetworkStateReceiver()
    private val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    private lateinit var navController: NavController // Make navController a class member
    private lateinit var nowPlayingViewModel: NowPlayingViewModel // To access current song

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            seedAssets(this)
        } else {
            Toast.makeText(this, "Permissions required to access media files.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PlayerController.initialize(applicationContext) // Initialize PlayerController early

        requestNotificationPermission()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup bottom navigation
        val navView: BottomNavigationView = binding.navView
        // Changed to NavHostFragment for correct NavController retrieval
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navView.setupWithNavController(navController)

        // Add mini player fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, com.tubes.purry.ui.player.MiniPlayerFragment())
                .commit()
        }

        // Initialize NowPlayingViewModel
        val appContext = applicationContext
        val likedSongDao = AppDatabase.getDatabase(appContext).LikedSongDao()
        val songDao = AppDatabase.getDatabase(appContext).songDao()
        val profileViewModelFactory = ProfileViewModelFactory(this) // Use alias
        val profileViewModel = ViewModelProvider(this, profileViewModelFactory)[ProfileViewModel::class.java]
        val nowPlayingFactory = NowPlayingViewModelFactory(likedSongDao, songDao, profileViewModel)
        nowPlayingViewModel = ViewModelProvider(this, nowPlayingFactory)[NowPlayingViewModel::class.java]

        // Add mini player fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, com.tubes.purry.ui.player.MiniPlayerFragment())
                .commit()
        }

        // Observe destination changes to hide/show mini player
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.songDetailFragment -> {
                    hideMiniPlayer()
                    hideBottomNav()
                }
                else -> {
                    // Only show mini player if there's a current song
                    if (PlayerController.currentlyPlaying != null) {
                        showMiniPlayer()
                    } else {
                        hideMiniPlayer()
                    }
                    showBottomNav()
                }
            }
        }

        nowPlayingViewModel.currSong.observe(this) { song ->
            if (navController.currentDestination?.id != R.id.songDetailFragment) {
                if (song != null) {
                    showMiniPlayer()
                } else {
                    hideMiniPlayer()
                }
            }
        }

        // Start token expiration service
        TokenExpirationService.startService(this)

        // Setup network status monitoring
        networkStateReceiver.addListener(this)
        registerReceiver(networkStateReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        // Observe network status changes
        NetworkUtil.getNetworkStatus().observe(this) { isAvailable ->
            if (isAvailable) {
                hideNetworkErrorBanner()
            } else {
                showNetworkErrorBanner()
            }
        }

        // Initial network check
        if (!NetworkUtil.isNetworkAvailable(this)) {
            showNetworkErrorBanner()
        }

        // Fetch user profile
        if (profileViewModel.profileData.value == null) { // Fetch only if not already fetched
            profileViewModel.getProfileData()
        }

        checkPermissionsAndSeed() // Check media permissions

        // Handle intent if app is opened from notification
        handleIntent(intent)

        // Trigger seeding
        // checkPermissionsAndSeed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called")
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("NAVIGATE_TO_PLAYER", false)) {
            Log.d("MainActivity", "Intent to navigate to player received.")
            // val songId = intent.getStringExtra("CURRENT_SONG_ID") // Not strictly needed if NowPlayingVM holds the state

            // Ensure NavController is ready and current song is available
            if (::navController.isInitialized && nowPlayingViewModel.currSong.value != null) {
                // Check if not already on songDetailFragment to prevent re-navigation
                if (navController.currentDestination?.id != R.id.songDetailFragment) {
                    navController.navigate(R.id.action_global_songDetailFragment)
                    Log.d("MainActivity", "Navigating to SongDetailFragment.")
                } else {
                    Log.d("MainActivity", "Already on SongDetailFragment.")
                }
            } else {
                Log.w("MainActivity", "Cannot navigate to player: NavController not ready or no current song.")
            }
        }
    }

    private fun checkPermissionsAndSeed() {
        val permissionsToRequest = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isEmpty() ||
            permissionsToRequest.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            seedAssets(this)
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onNetworkAvailable() = runOnUiThread { hideNetworkErrorBanner() }
    override fun onNetworkUnavailable() = runOnUiThread { showNetworkErrorBanner() }

    private fun showNetworkErrorBanner() {
        binding.networkErrorBanner.visibility = View.VISIBLE
    }

    private fun hideNetworkErrorBanner() {
        binding.networkErrorBanner.visibility = View.GONE
    }

    fun showMiniPlayer() {
        binding.miniPlayerContainer.apply {
            if (visibility != View.VISIBLE) {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(250).start()
            }
        }
    }

    fun hideMiniPlayer() {
        binding.miniPlayerContainer.visibility = View.GONE
    }

    fun hideBottomNav() {
        binding.navView.visibility = View.GONE
    }

    fun showBottomNav() {
        binding.navView.visibility = View.VISIBLE
    }


    override fun onStart() {
        super.onStart()
        // Re-register receiver if it was unregistered in onStop
        try {
            registerReceiver(networkStateReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), RECEIVER_NOT_EXPORTED)
        } catch (e: IllegalArgumentException) {
            // Receiver already registered
            Log.w("MainActivity", "NetworkStateReceiver already registered or error: ${e.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(networkStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
            Log.w("MainActivity", "NetworkStateReceiver not registered or error on unregister: ${e.message}")
        }
        // networkStateReceiver.removeListener(this) // Listener removed in onDestroy usually
    }

    override fun onDestroy() {
        super.onDestroy()
        networkStateReceiver.removeListener(this)
        // Decide if playback should stop when MainActivity is destroyed
        // If you have a foreground service managing playback, this might not be needed.
        // If playback is tied to Activity lifecycle and no service, then:
        // PlayerController.fullyReleaseSession() // Or just PlayerController.release() if session should persist
        Log.d("MainActivity", "onDestroy called.")
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d("Permissions", "POST_NOTIFICATIONS granted")
                // Optionally re-show the notification here
            } else {
                Log.w("Permissions", "POST_NOTIFICATIONS denied")
            }
        }
    }
}
