package com.tubes.purry.ui.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.FragmentProfileBinding
import com.tubes.purry.data.repository.SongRepository
import com.tubes.purry.ui.auth.LoginActivity
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.SessionManager
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private lateinit var sessionManager: SessionManager

    // Add a constant for request code
    private val EDIT_PROFILE_REQUEST_CODE = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        val factory = ProfileViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        setupClickListeners()
        observeViewModel()

        if (NetworkUtil.isNetworkAvailable(requireContext())) {
            loadProfileData()
        } else {
            showNetworkError()
        }
    }

    private fun setupClickListeners() {
        // Set up logout button
        binding.btnLogout.setOnClickListener {
            com.tubes.purry.ui.player.PlayerController.release()
            sessionManager.clearTokens()
            navigateToLogin()
        }

        // Set up edit profile button
        binding.btnEditProfile.setOnClickListener {
            navigateToEditProfile()
        }

        // Set up edit photo button
        binding.btnEditPhoto.setOnClickListener {
            navigateToEditProfile()
        }
    }

    private fun navigateToEditProfile() {
        val intent = Intent(requireContext(), EditProfileActivity::class.java)
        startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_PROFILE_REQUEST_CODE) {
            // Refresh profile data after returning from edit screen
            if (NetworkUtil.isNetworkAvailable(requireContext())) {
                loadProfileData()
            }
        }
    }

    private fun loadProfileData() {
        val token = sessionManager.fetchAuthToken()
        if (token != null) {
            Log.d("ProfileFragment", "Token exists, attempting to load profile")
            viewModel.getProfileData()
        } else {
            Log.d("ProfileFragment", "No token found, navigating to login")
            navigateToLogin()
        }
    }

    private fun observeViewModel() {
        viewModel.profileData.observe(viewLifecycleOwner) { profile ->
            profile?.let {
                // 1. Tampilkan ke UI
                binding.apply {
                    txtUsername.text = it.username
                    // Convert country code to country name for better display
                    txtLocation.text = getCountryNameFromCode(it.location)

                    val imageUrl = "http://34.101.226.132:3000/uploads/profile-picture/${it.profilePhoto}"
                    Glide.with(this@ProfileFragment)
                        .load(imageUrl)
                        .placeholder(R.drawable.profile_placeholder)
                        .error(R.drawable.profile_placeholder)
                        .circleCrop()
                        .into(imgProfile)
                }

                // 2. ⬅️ Panggil untuk update song stats by user ID
                viewModel.fetchSongStats(it.id)
            }
        }

        viewModel.songStats.observe(viewLifecycleOwner) { stats ->
            binding.apply {
                txtSongsCount.text = stats.totalCount.toString()
                txtLikedCount.text = stats.likedCount.toString()
                txtListenedCount.text = stats.listenedCount.toString()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun getCountryNameFromCode(countryCode: String): String {
        return try {
            val locale = Locale("", countryCode)
            locale.displayCountry
        } catch (e: Exception) {
            countryCode // Fallback to showing the code if conversion fails
        }
    }

    private fun showNetworkError() {
        binding.networkErrorLayout.visibility = View.VISIBLE
        binding.profileLayout.visibility = View.GONE
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}