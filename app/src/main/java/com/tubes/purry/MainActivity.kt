package com.tubes.purry

import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.ktx.Firebase
import com.tubes.purry.databinding.ActivityMainBinding
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.TokenExpirationService
import com.tubes.purry.utils.PermissionManager

class MainActivity : AppCompatActivity(), NetworkStateReceiver.NetworkStateListener {

    private lateinit var binding: ActivityMainBinding
    private val networkStateReceiver = NetworkStateReceiver()
    private val permissionManager = PermissionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent = intent

        // Setup bottom navigation
        val navView: BottomNavigationView = binding.navView
        val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            ?.findNavController() ?: throw IllegalStateException("NavController not found")
        navView.setupWithNavController(navController)

        // Add mini player fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, com.tubes.purry.ui.player.MiniPlayerFragment())
                .commit()
        }

        // Request necessary permissions
        if (!permissionManager.checkBluetoothPermissions(this)) {
            permissionManager.requestBluetoothPermissions(this)
        }

        if (!permissionManager.checkAudioPermissions(this)) {
            permissionManager.requestAudioPermissions(this)
        }

        // Observe destination changes to hide/show mini player
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.songDetailFragment -> {
                    hideMiniPlayer()
                    hideBottomNav()
                }
                else -> {
                    showMiniPlayer()
                    showBottomNav()
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
        val profileViewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(application)
        )[ProfileViewModel::class.java]
        profileViewModel.getProfileData()

        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener { pendingDynamicLinkData ->
                val deepLink: Uri? = pendingDynamicLinkData?.link
                if (deepLink != null) {
                    handleDeepLink(deepLink)
                }
            }
            .addOnFailureListener {
                // Optional: log error
            }

        handleIntentNavigation(intent)
    }

    // FIXED: Moved this method outside of onCreate()
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissionManager.handlePermissionResult(
            requestCode,
            grantResults,
            onBluetoothGranted = {
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
            },
            onBluetoothDenied = {
                Toast.makeText(this, "Bluetooth permissions required for external audio devices", Toast.LENGTH_LONG).show()
            },
            onAudioGranted = {
                Toast.makeText(this, "Audio permissions granted", Toast.LENGTH_SHORT).show()
            },
            onAudioDenied = {
                Toast.makeText(this, "Audio permissions required for output control", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentNavigation(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(data: Uri?) {
        if (data != null && data.scheme == "purrytify" && data.host == "song") {
            val songId = data.lastPathSegment?.toIntOrNull()
            if (songId != null) {
                val navController = findNavController(R.id.nav_host_fragment)
                val bundle = Bundle().apply {
                    putInt("songId", songId)
                }
                navController.navigate(R.id.songDetailFragment, bundle)
            }
        }
    }

    private fun handleDeepLink(data: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "purrytify" && data.host == "song") {
            val songId = data.lastPathSegment?.toIntOrNull()
            if (songId != null) {
                val navController = findNavController(R.id.nav_host_fragment)
                val bundle = Bundle().apply {
                    putInt("songId", songId)
                }
                navController.navigate(R.id.songDetailFragment, bundle)
            }
        }
    }

    private fun handleIntentNavigation(intent: Intent?) {
        intent?.getStringExtra("navigateTo")?.let { destination ->
            if (destination == "detail") {
                val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    ?.findNavController()
                if (navController?.currentDestination?.id != R.id.songDetailFragment) {
                    navController?.navigate(R.id.songDetailFragment)
                }
            }
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

    private fun showMiniPlayer() {
        binding.miniPlayerContainer.apply {
            if (visibility != View.VISIBLE) {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(250).start()
            }
        }
    }

    private fun hideMiniPlayer() {
        binding.miniPlayerContainer.visibility = View.GONE
    }

    private fun hideBottomNav() {
        binding.navView.visibility = View.GONE
    }

    private fun showBottomNav() {
        binding.navView.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(networkStateReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(networkStateReceiver)
        networkStateReceiver.removeListener(this)
    }
}