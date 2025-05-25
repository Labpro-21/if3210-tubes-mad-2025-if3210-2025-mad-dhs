package com.tubes.purry

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.ActivityMainBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.AudioDeviceBroadcastReceiver
import com.tubes.purry.utils.AudioOutputManager
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.TokenExpirationService

class MainActivity : AppCompatActivity(), NetworkStateReceiver.NetworkStateListener {
    private lateinit var binding: ActivityMainBinding
    private val networkStateReceiver = NetworkStateReceiver()
    private lateinit var audioDeviceReceiver: AudioDeviceBroadcastReceiver
    private lateinit var navController: NavController

    // Lazily initialize NowPlayingViewModel
    private val nowPlayingViewModel: NowPlayingViewModel by viewModels {
        val appContext = applicationContext
        val db = AppDatabase.getDatabase(appContext)
        val profileFactory = ProfileViewModelFactory(appContext)
        val profileViewModel = ViewModelProvider(this, profileFactory)[ProfileViewModel::class.java]
        NowPlayingViewModelFactory(db.LikedSongDao(), db.songDao(), profileViewModel, appContext)
    }

    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("MainActivity", "All required Bluetooth permissions granted.")
            showAudioDeviceDialog() // Proceed to show dialog if permissions are now granted
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to select audio output.", Toast.LENGTH_LONG).show()
            Log.d("MainActivity", "Not all Bluetooth permissions were granted.")
            permissions.entries.forEach {
                Log.d("MainActivity", "Permission: ${it.key}, Granted: ${it.value}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize and register AudioDeviceBroadcastReceiver
        audioDeviceReceiver = AudioDeviceBroadcastReceiver()
        val audioIntentFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED) // For BT on/off
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioDeviceReceiver, audioIntentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(audioDeviceReceiver, audioIntentFilter)
        }

        // Setup bottom navigation
        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController // Initialize navController

        NavigationUI.setupWithNavController(navView, navController)

        // Custom listener to handle the audio output item specifically
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_audio_output -> {
                    showAudioDeviceDialog()
                    // Return false because we are not navigating to a new fragment for this item.
                    // We want to stay on the current fragment.
                    // However, the BottomNavigationView might still try to visually select it.
                    // To prevent actual navigation and keep the current fragment selected:
                    // You might need to manage the selected item state carefully or use onNavigationItemSelected.
                    // For simplicity, let's see if returning true (consumed) but not navigating works.
                    // A common pattern is to let it navigate to current item if reselected.
                    // For a non-navigation action, this gets tricky with setupWithNavController.
                    // An alternative is to NOT use setupWithNavController and handle all item selections manually.
                    true // Indicate we've handled this item
                }
                // Let NavigationUI handle other items for fragment navigation
                R.id.navigation_home, R.id.navigation_library, R.id.navigation_profile -> {
                    NavigationUI.onNavDestinationSelected(item, navController)
                    // true
                }
            }
            if (item.itemId == R.id.navigation_audio_output) {
                showAudioDeviceDialog()
                return@setOnItemSelectedListener true // We handled it.
            }
            return@setOnItemSelectedListener NavigationUI.onNavDestinationSelected(item, navController)
        }

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

        nowPlayingViewModel.updateActiveAudioOutput()

        // Trigger seeding
        // checkPermissionsAndSeed()
    }

    fun showAudioDeviceDialog() {
        if (!checkAndRequestBluetoothPermissions()) {
            Toast.makeText(this, "Bluetooth permissions are needed to list devices.", Toast.LENGTH_SHORT).show()
            return
        }
        val devices = AudioOutputManager.getAvailableAudioDevices(this)
        if (devices.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth audio devices found. Please pair a device in settings.", Toast.LENGTH_LONG).show()
            AudioOutputManager.openBluetoothSettings(this)
            return
        }
        val dialog = com.tubes.purry.ui.outputdevice.SelectAudioDeviceDialog()
        dialog.show(supportFragmentManager, "SelectAudioDeviceDialog")
    }

    private fun checkAndRequestBluetoothPermissions(): Boolean {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // BLUETOOTH_SCAN might be needed for more active discovery, but for bondedDevices, CONNECT is key.
            // If you were to scan for *new* devices:
            // if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            // requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            // }
        } else { // Pre-Android 12
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            Log.d("MainActivity", "Requesting Bluetooth permissions: $requiredPermissions")
            requestBluetoothPermissionLauncher.launch(requiredPermissions.toTypedArray())
            return false
        }
        return true
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

    // Companion object to allow BroadcastReceiver to access MainActivity instance
    companion object {
        var activityInstance: MainActivity? = null
    }

    override fun onResume() {
        super.onResume()
        activityInstance = this
        nowPlayingViewModel.updateActiveAudioOutput() // Refresh audio output on resume
    }

    override fun onPause() {
        super.onPause()
        activityInstance = null
    }
}
