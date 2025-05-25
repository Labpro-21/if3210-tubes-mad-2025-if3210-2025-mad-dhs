package com.tubes.purry

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.ktx.Firebase
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.ActivityMainBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.TokenExpirationService
import com.tubes.purry.ui.player.NowPlayingManager

class MainActivity : AppCompatActivity(), NetworkStateReceiver.NetworkStateListener {

    private lateinit var binding: ActivityMainBinding
    private val networkStateReceiver = NetworkStateReceiver()
    private lateinit var nowPlayingViewModel: NowPlayingViewModel


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
        val navView = findViewById<BottomNavigationView?>(R.id.navView)
        val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            ?.findNavController() ?: throw IllegalStateException("NavController not found")
        navView?.setupWithNavController(navController)

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
        val profileViewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(application)
        )[ProfileViewModel::class.java]
        profileViewModel.getProfileData()
        nowPlayingViewModel = ViewModelProvider(
            this,
            NowPlayingViewModelFactory(application,
                AppDatabase.getDatabase(this).LikedSongDao(),
                AppDatabase.getDatabase(this).songDao(),
                profileViewModel)
        )[NowPlayingViewModel::class.java]

        NowPlayingManager.setViewModel(nowPlayingViewModel)

        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener { pendingDynamicLinkData ->
                val deepLink: Uri? = pendingDynamicLinkData?.link
                if (deepLink != null) {
                    // TUNDA navigasi sampai View sudah siap
                    binding.root.post {
                        handleDeepLink(deepLink)
                    }
                }
            }

            .addOnFailureListener {
                // Opsional: log error
            }


        handleIntentNavigation(intent)

        // Trigger seeding
        // checkPermissionsAndSeed()
        binding.root.post {
            handleDeepLink(intent?.data)
        }

        binding.menuHome?.setOnClickListener {
            val navController = findNavController(R.id.nav_host_fragment)
            if (navController.currentDestination?.id != R.id.navigation_home) {
                navController.navigate(R.id.navigation_home)
            }
        }
        binding.menuLibrary?.setOnClickListener {
            val navController = findNavController(R.id.nav_host_fragment)
            if (navController.currentDestination?.id != R.id.navigation_library) {
                navController.navigate(R.id.navigation_library)
            }
        }
        binding.menuProfile?.setOnClickListener {
            val navController = findNavController(R.id.nav_host_fragment)
            if (navController.currentDestination?.id != R.id.navigation_profile) {
                navController.navigate(R.id.navigation_profile)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home -> highlightMenu("home")
                R.id.navigation_library -> highlightMenu("library")
                R.id.navigation_profile -> highlightMenu("profile")
                else -> resetMenuHighlight()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent?.data)
    }

    private fun highlightMenu(menu: String) {
        val activeColor = ContextCompat.getColor(this, R.color.green)
        val inactiveColor = ContextCompat.getColor(this, R.color.white_gray)

        binding.menuHome?.setTextColor(if (menu == "home") activeColor else inactiveColor)
        binding.menuLibrary?.setTextColor(if (menu == "library") activeColor else inactiveColor)
        binding.menuProfile?.setTextColor(if (menu == "profile") activeColor else inactiveColor)

        binding.menuHome?.setTypeface(null, if (menu == "home") Typeface.BOLD else Typeface.NORMAL)
        binding.menuLibrary?.setTypeface(null, if (menu == "library") Typeface.BOLD else Typeface.NORMAL)
        binding.menuProfile?.setTypeface(null, if (menu == "profile") Typeface.BOLD else Typeface.NORMAL)
    }

    private fun resetMenuHighlight() {
        val color = ContextCompat.getColor(this, R.color.white_gray)
        binding.menuHome?.setTextColor(color)
        binding.menuLibrary?.setTextColor(color)
        binding.menuProfile?.setTextColor(color)

        binding.menuHome?.setTypeface(null, Typeface.NORMAL)
        binding.menuLibrary?.setTypeface(null, Typeface.NORMAL)
        binding.menuProfile?.setTypeface(null, Typeface.NORMAL)
    }

    private fun handleDeepLink(data: Uri?) {
        if (data != null && data.scheme == "purrytify" && data.host == "song") {
            val rawId = data.lastPathSegment
            val songId = rawId?.toIntOrNull()

            if (songId != null) {
                val navController = findNavController(R.id.nav_host_fragment)
                val bundle = Bundle().apply {
                    putInt("songId", songId)
                }

                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(navController.graph.startDestinationId, false)
                    .build()

                navController.navigate(R.id.songDetailFragment, bundle, navOptions)
            } else {
                Log.e("DeepLink", "Invalid songId in deep link: $rawId")
                Toast.makeText(this, "Link tidak valid", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun handleIntentNavigation(intent: Intent?) {
        intent?.getStringExtra("navigateTo")?.let { destination ->
            if (destination == "detail") {
                val songId = intent.getIntExtra("songId", -1) // Get songId from intent
                if (songId != -1) {
                    val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        ?.findNavController()
                    if (navController?.currentDestination?.id != R.id.songDetailFragment) {
                        val bundle = Bundle().apply {
                            putInt("songId", songId)
                        }
                        navController?.navigate(R.id.songDetailFragment, bundle)
                    }
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
        binding.navView?.visibility = View.GONE
    }

    private fun showBottomNav() {
        binding.navView?.visibility = View.VISIBLE
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
