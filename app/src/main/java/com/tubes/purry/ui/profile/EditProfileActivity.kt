package com.tubes.purry.ui.profile

import android.Manifest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.tubes.purry.R
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.data.repository.AuthRepository
import com.tubes.purry.data.repository.ProfileRepository
import com.tubes.purry.databinding.ActivityEditProfileBinding
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var profileRepository: ProfileRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager

    private var currentPhotoUri: Uri? = null
    private var currentLocation: String? = null
    private var currentPhotoPath: String? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Permission granted
                getLastLocation()
            }
            else -> {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
                showPermissionExplanationDialog()
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoUri?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentPhotoUri = uri
                loadImageFromUri(uri)
            }
        }
    }

    private val mapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val countryCode = data.getStringExtra("selected_country_code")
                val countryName = data.getStringExtra("selected_country_name")
                val latitude = data.getDoubleExtra("selected_latitude", 0.0)
                val longitude = data.getDoubleExtra("selected_longitude", 0.0)

                countryCode?.let { code ->
                    currentLocation = code
                    binding.txtCurrentLocation.text = countryName ?: getCountryNameFromCode(code)

                    // Optional: Log the coordinates for debugging
                    Log.d("EditProfile", "Selected location: $latitude, $longitude in $countryName ($code)")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize sessionManager first
        sessionManager = SessionManager(applicationContext)

        // Create AuthRepository
        authRepository = AuthRepository(ApiClient.apiService, sessionManager)

        // Create ProfileRepository with all required parameters
        profileRepository = ProfileRepository(ApiClient.apiService, authRepository)

        setupClickListeners()
        loadCurrentProfileData()
    }

    private fun setupClickListeners() {
        binding.btnChangePhoto.setOnClickListener {
            showImageSelectionDialog()
        }

        binding.btnDetectLocation.setOnClickListener {
            checkLocationPermission()
        }

        binding.btnChooseLocation.setOnClickListener {
            // Launch Google Maps activity
            val intent = Intent(this, MapsActivity::class.java)
            mapLauncher.launch(intent)
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfileChanges()
        }
    }

    private fun loadCurrentProfileData() {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Using the new method that handles token internally
                val response = profileRepository.getProfile()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val profile = response.body()!!
                        currentLocation = profile.location
                        binding.txtCurrentLocation.text = getCountryNameFromCode(profile.location)

                        // Load profile image
                        val imageUrl = "http://34.101.226.132:3000/uploads/profile-picture/${profile.profilePhoto}"
                        Glide.with(this@EditProfileActivity)
                            .load(imageUrl)
                            .placeholder(R.drawable.profile_placeholder)
                            .error(R.drawable.profile_placeholder)
                            .circleCrop()
                            .into(binding.imgProfile)
                    } else {
                        Toast.makeText(
                            this@EditProfileActivity,
                            "Failed to load profile: ${response.message()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {

            // Request permissions
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Permission already granted
            getLastLocation()
        }
    }

    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            return
        }

        binding.txtCurrentLocation.text = "Detecting location..."

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    getCountryCodeFromLocation(it.latitude, it.longitude)
                } ?: run {
                    Toast.makeText(this, "Location not available. Try again later.", Toast.LENGTH_SHORT).show()
                    binding.txtCurrentLocation.text = "Location not available"
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.txtCurrentLocation.text = "Error detecting location"
            }
    }

    private fun getCountryCodeFromLocation(latitude: Double, longitude: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses != null && addresses.isNotEmpty()) {
                val countryCode = addresses[0].countryCode
                val countryName = addresses[0].countryName

                currentLocation = countryCode
                binding.txtCurrentLocation.text = countryName
            } else {
                binding.txtCurrentLocation.text = "Location not found"
            }
        } catch (e: IOException) {
            binding.txtCurrentLocation.text = "Error getting location"
            e.printStackTrace()
        }
    }

    private fun getCountryNameFromCode(countryCode: String): String {
        val locale = Locale("", countryCode)
        return locale.displayCountry
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")

        AlertDialog.Builder(this)
            .setTitle("Change Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> checkGalleryPermission()
                }
            }
            .show()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            dispatchTakePictureIntent()
        }
    }

    private fun checkGalleryPermission() {
        // For Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQUEST_GALLERY_PERMISSION
                )
            } else {
                openGallery()
            }
        } else {
            // For Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_GALLERY_PERMISSION
                )
            } else {
                openGallery()
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Check if there's a camera app available to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
                    null
                }

                photoFile?.also {
                    currentPhotoUri = FileProvider.getUriForFile(
                        this,
                        "com.tubes.purry.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                    takePictureLauncher.launch(takePictureIntent)
                }
            } ?: run {
                // No camera app available
                Toast.makeText(this, "No camera application available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(null)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun loadImageFromUri(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(binding.imgProfile)
    }

    private fun saveProfileChanges() {
        if (currentLocation == null) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
            return
        }

        val token = sessionManager.fetchAuthToken()
        if (token == null) {
            Toast.makeText(this, "Authentication token not found", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val locationPart = currentLocation!!.toRequestBody("text/plain".toMediaTypeOrNull())

                var profilePhotoPart: MultipartBody.Part? = null
                if (currentPhotoUri != null) {
                    try {
                        // Instead of trying to get a file path, read the content directly using input stream
                        val inputStream = contentResolver.openInputStream(currentPhotoUri!!)
                        if (inputStream != null) {
                            // Create a temp file to store the contents
                            val tempFile = File.createTempFile("upload_", ".jpg", cacheDir)
                            tempFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }

                            // Use the temp file for the upload
                            val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                            profilePhotoPart = MultipartBody.Part.createFormData(
                                "profilePhoto",
                                "profile_image.jpg",
                                requestFile
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("EditProfileActivity", "Error handling image file", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@EditProfileActivity,
                                "Error processing image: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        // Continue without the image if there's an error
                    }
                }

                // Make the API call
                val response = ApiClient.apiService.updateProfile(
                    "Bearer $token",
                    locationPart,
                    profilePhotoPart
                )

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@EditProfileActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@EditProfileActivity, "Failed to update profile: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("EditProfileActivity", "Error updating profile", e)
                }
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("To detect your current location, we need location permission. Would you like to grant permission in settings?")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_GALLERY_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "Gallery permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 101
        private const val REQUEST_GALLERY_PERMISSION = 102
    }
}