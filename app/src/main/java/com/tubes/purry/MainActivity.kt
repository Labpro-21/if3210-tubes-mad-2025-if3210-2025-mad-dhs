package com.tubes.purry

import android.os.Bundle
import android.view.View
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tubes.purry.databinding.ActivityMainBinding
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.TokenExpirationService

class MainActivity : AppCompatActivity(), NetworkStateReceiver.NetworkStateListener {

    private lateinit var binding: ActivityMainBinding
    private val networkStateReceiver = NetworkStateReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup bottom navigation
        val navView: BottomNavigationView = binding.navView
//        val navController = findNavController(R.id.nav_host_fragment)
        val navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            ?.findNavController() ?: throw IllegalStateException("NavController not found")
        navView.setupWithNavController(navController)


        // Start token expiration service
        TokenExpirationService.startService(this)

        // Setup network status monitoring
        networkStateReceiver.addListener(this)

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
    }

    override fun onNetworkAvailable() {
        runOnUiThread {
            hideNetworkErrorBanner()
        }
    }

    override fun onNetworkUnavailable() {
        runOnUiThread {
            showNetworkErrorBanner()
        }
    }

    private fun showNetworkErrorBanner() {
        binding.networkErrorBanner.visibility = View.VISIBLE
    }

    private fun hideNetworkErrorBanner() {
        binding.networkErrorBanner.visibility = View.GONE
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