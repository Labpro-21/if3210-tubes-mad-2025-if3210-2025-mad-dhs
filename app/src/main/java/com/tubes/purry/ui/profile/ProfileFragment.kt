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
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.databinding.FragmentProfileBinding
import com.tubes.purry.data.repository.ProfileRepository
import com.tubes.purry.data.repository.SongRepository
import com.tubes.purry.ui.auth.LoginActivity
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.SessionManager

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private lateinit var sessionManager: SessionManager

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

        // Initialize repositories
        val profileRepository = ProfileRepository(ApiClient.apiService)
        val songRepository = SongRepository(AppDatabase.getDatabase(requireContext()).songDao())

        // Initialize ViewModel with Factory
        val viewModelFactory = ProfileViewModelFactory(profileRepository, songRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileViewModel::class.java]

        binding.btnLogout.setOnClickListener {
            sessionManager.clearTokens()
            navigateToLogin()
        }

        observeViewModel()

        if (NetworkUtil.isNetworkAvailable(requireContext())) {
            loadProfileData()
        } else {
            showNetworkError()
        }
    }

    private fun loadProfileData() {
        val token = sessionManager.fetchAuthToken()
        if (token != null) {
            viewModel.getProfileData("Bearer $token")
        } else {
            Log.d("ProfileFragment", "No token found, navigating to login")
            navigateToLogin()
        }
    }

    private fun observeViewModel() {
        viewModel.profileData.observe(viewLifecycleOwner) { profile ->
            binding.apply {
                txtUsername.text = profile.username
                txtLocation.text = profile.location

                // Load profile image with Glide
                val imageUrl = "http://34.101.226.132:3000/uploads/profile-picture/${profile.profilePhoto}"
                Glide.with(this@ProfileFragment)
                    .load(imageUrl)
                    .placeholder(R.drawable.profile_placeholder)
                    .error(R.drawable.profile_placeholder)
                    .circleCrop()
                    .into(imgProfile)
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