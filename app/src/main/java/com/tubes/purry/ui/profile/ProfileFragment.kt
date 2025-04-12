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

        val factory = ProfileViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

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
                    txtLocation.text = it.location

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