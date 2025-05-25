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
import com.tubes.purry.ui.player.MiniPlayerFragment
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.TokenExpirationService
import com.tubes.purry.ui.player.NowPlayingManager
import com.tubes.purry.ui.qr.ScanQRActivity

class MainActivity : AppCompatActivity(), NetworkStateReceiver.NetworkStateListener {

    private lateinit var binding: ActivityMainBinding
    private val networkStateReceiver = NetworkStateReceiver()
    private lateinit var nowPlayingViewModel: NowPlayingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nowPlayingViewModel = (application as PurrytifyApplication).nowPlayingViewModel
        NowPlayingManager.setViewModel(nowPlayingViewModel)

        binding.miniPlayerContainer.visibility = View.GONE
        nowPlayingViewModel.currSong.observe(this) { song ->
            Log.d("MainActivity", "currSong.observe: ${song?.title}")
            if (song != null) {
                showMiniPlayer()
            } else {
                hideMiniPlayer()
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
            .commit()

        val navView = findViewById<BottomNavigationView?>(R.id.navView)
        val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            ?.findNavController() ?: throw IllegalStateException("NavController not found")
        navView?.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.songDetailFragment -> {
                    hideMiniPlayer()
                    hideBottomNav()
                    binding.btnScanQr?.hide()
                }
                else -> {
                    showMiniPlayer()
                    showBottomNav()
                    binding.btnScanQr?.show()
                }
            }

            when (destination.id) {
                R.id.navigation_home -> highlightMenu("home")
                R.id.navigation_library -> highlightMenu("library")
                R.id.navigation_profile -> highlightMenu("profile")
                else -> resetMenuHighlight()
            }
        }

        TokenExpirationService.startService(this)
        networkStateReceiver.addListener(this)
        registerReceiver(networkStateReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        NetworkUtil.getNetworkStatus().observe(this) { isAvailable ->
            if (isAvailable) hideNetworkErrorBanner() else showNetworkErrorBanner()
        }

        if (!NetworkUtil.isNetworkAvailable(this)) showNetworkErrorBanner()

        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener { pendingDynamicLinkData ->
                val deepLink: Uri? = pendingDynamicLinkData?.link
                if (deepLink != null) {
                    binding.root.post { handleDeepLink(deepLink) }
                }
            }

        handleIntentNavigation(intent)
        binding.root.post { handleDeepLink(intent?.data) }

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
        binding.btnScanQr?.setOnClickListener {
            val intent = Intent(this, ScanQRActivity::class.java)
            startActivity(intent)
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
                    putInt("songIdInt", songId)
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
                val songId = intent.getIntExtra("songId", -1)
                if (songId != -1) {
                    val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        ?.findNavController()
                    if (navController?.currentDestination?.id != R.id.songDetailFragment) {
                        val bundle = Bundle().apply {
                            putString("songId", songId.toString())
                        }
                        navController?.navigate(R.id.songDetailFragment, bundle)
                    }
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
        binding.navView?.visibility = View.GONE
    }

    private fun showBottomNav() {
        binding.navView?.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            networkStateReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(networkStateReceiver)
        networkStateReceiver.removeListener(this)
    }
}