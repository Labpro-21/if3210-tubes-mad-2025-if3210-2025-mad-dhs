package com.tubes.purry

//import android.Manifest
import android.content.Intent
import android.content.IntentFilter
//import android.content.pm.PackageManager
import android.net.ConnectivityManager
//import android.os.Build
import android.os.Bundle
import android.view.View
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tubes.purry.databinding.ActivityMainBinding
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.TokenExpirationService
//import com.tubes.purry.utils.seedAssets

class MainActivity : AppCompatActivity(), NetworkStateReceiver.NetworkStateListener {

    private lateinit var binding: ActivityMainBinding
    private val networkStateReceiver = NetworkStateReceiver()

//    private val permissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { permissions ->
//        val granted = permissions.values.all { it }
//        if (granted) {
//            seedAssets(this)
//        } else {
//            Toast.makeText(this, "Permissions required to access media files.", Toast.LENGTH_LONG).show()
//        }
//    }

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
        val profileViewModel = ViewModelProvider(this, ProfileViewModelFactory(this))[ProfileViewModel::class.java]
        profileViewModel.getProfileData()

        handleIntentNavigation(intent)

        // Trigger seeding
        // checkPermissionsAndSeed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentNavigation(intent)
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

//    private fun checkPermissionsAndSeed() {
//        val permissionsToRequest = mutableListOf<String>().apply {
//            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
//                add(Manifest.permission.READ_EXTERNAL_STORAGE)
//            }
//        }
//
//        if (permissionsToRequest.isEmpty() ||
//            permissionsToRequest.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
//            seedAssets(this)
//        } else {
//            permissionLauncher.launch(permissionsToRequest.toTypedArray())
//        }
//    }

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
