package com.tubes.purry.ui.profile

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.tubes.purry.R
import com.tubes.purry.databinding.ActivityMapsBinding
import java.io.IOException
import java.util.Locale

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapsBinding
    private var googleMap: GoogleMap? = null
    private var selectedLocation: LatLng? = null
    private var selectedCountryCode: String? = null
    private lateinit var geocoder: Geocoder

    companion object {
        private const val TAG = "MapsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting MapsActivity")

        try {
            binding = ActivityMapsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "onCreate: Layout inflated successfully")

            geocoder = Geocoder(this, Locale.getDefault())

            // Add delay to ensure layout is ready
            Handler(Looper.getMainLooper()).postDelayed({
                initializeMap()
            }, 500)

            setupClickListeners()

        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error initializing activity", e)
            Toast.makeText(this, "Error initializing maps: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeMap() {
        try {
            // Initialize map
            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.mapFragment) as? SupportMapFragment

            if (mapFragment != null) {
                Log.d(TAG, "initializeMap: MapFragment found, getting map async")
                mapFragment.getMapAsync(this)
            } else {
                Log.e(TAG, "initializeMap: MapFragment is null!")

                // Try alternative approach
                Handler(Looper.getMainLooper()).postDelayed({
                    val fragmentRetry = supportFragmentManager
                        .findFragmentById(R.id.mapFragment) as? SupportMapFragment
                    fragmentRetry?.getMapAsync(this)
                }, 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "initializeMap: Error", e)
        }
    }

    private fun setupClickListeners() {
        Log.d(TAG, "setupClickListeners: Setting up click listeners")

        binding.btnSelectLocation.setOnClickListener {
            Log.d(TAG, "btnSelectLocation clicked")
            selectedLocation?.let { location ->
                getCountryCodeFromLocation(location.latitude, location.longitude)
            } ?: run {
                Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancel.setOnClickListener {
            Log.d(TAG, "btnCancel clicked")
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d(TAG, "onMapReady: Map is ready!")

        try {
            googleMap = map

            // Test if map is actually working by setting a background color
            googleMap?.mapType = GoogleMap.MAP_TYPE_NORMAL

            // Set default location (Jakarta, Indonesia)
            val defaultLocation = LatLng(-6.2088, 106.8456)

            // Add a marker first to see if it appears
            googleMap?.addMarker(
                MarkerOptions()
                    .position(defaultLocation)
                    .title("Default Location - Jakarta")
            )

            // Move camera with animation
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f),
                2000,
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        Log.d(TAG, "Camera animation finished")
                        Toast.makeText(this@MapsActivity, "Map loaded! Tap to select location", Toast.LENGTH_SHORT).show()
                    }

                    override fun onCancel() {
                        Log.d(TAG, "Camera animation cancelled")
                    }
                }
            )

            Log.d(TAG, "onMapReady: Camera moved to default location")

            // Set map click listener
            googleMap?.setOnMapClickListener { latLng ->
                Log.d(TAG, "Map clicked at: ${latLng.latitude}, ${latLng.longitude}")

                // Clear previous markers
                googleMap?.clear()

                // Add marker at clicked location
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Selected Location")
                )

                selectedLocation = latLng

                // Update UI to show coordinates
                binding.txtSelectedCoordinates.text =
                    "Lat: ${String.format("%.6f", latLng.latitude)}, Lng: ${String.format("%.6f", latLng.longitude)}"

                // Get address from coordinates
                getAddressFromLocation(latLng.latitude, latLng.longitude)
            }

            // Enable map controls
            googleMap?.uiSettings?.apply {
                isZoomControlsEnabled = true
                isMapToolbarEnabled = true
                isMyLocationButtonEnabled = false
                isCompassEnabled = true
                isRotateGesturesEnabled = true
                isScrollGesturesEnabled = true
                isTiltGesturesEnabled = true
                isZoomGesturesEnabled = true
            }

            Log.d(TAG, "onMapReady: Map setup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "onMapReady: Error setting up map", e)
            Toast.makeText(this, "Error setting up map: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        Log.d(TAG, "getAddressFromLocation: Getting address for $latitude, $longitude")

        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val fullAddress = address.getAddressLine(0) ?: ""
                val countryName = address.countryName ?: ""

                binding.txtSelectedAddress.text = fullAddress
                binding.txtSelectedCountry.text = countryName

                Log.d(TAG, "getAddressFromLocation: Country: $countryName, Address: $fullAddress")
            } else {
                Log.w(TAG, "getAddressFromLocation: No addresses found")
                binding.txtSelectedAddress.text = "Address not available"
            }
        } catch (e: IOException) {
            Log.e(TAG, "getAddressFromLocation: Error getting address", e)
            binding.txtSelectedAddress.text = "Address not available"
        }
    }

    private fun getCountryCodeFromLocation(latitude: Double, longitude: Double) {
        Log.d(TAG, "getCountryCodeFromLocation: Getting country code for $latitude, $longitude")

        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val countryCode = addresses[0].countryCode
                val countryName = addresses[0].countryName

                if (countryCode != null) {
                    selectedCountryCode = countryCode

                    Log.d(TAG, "getCountryCodeFromLocation: Found country: $countryName ($countryCode)")

                    // Return result to EditProfileActivity
                    val resultIntent = Intent()
                    resultIntent.putExtra("selected_country_code", countryCode)
                    resultIntent.putExtra("selected_country_name", countryName)
                    resultIntent.putExtra("selected_latitude", latitude)
                    resultIntent.putExtra("selected_longitude", longitude)

                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                } else {
                    Log.w(TAG, "getCountryCodeFromLocation: Country code is null")
                    Toast.makeText(this, "Could not determine country for this location", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "getCountryCodeFromLocation: No addresses found")
                Toast.makeText(this, "Could not get location details", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e(TAG, "getCountryCodeFromLocation: Error getting country code", e)
            Toast.makeText(this, "Error getting location details: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}