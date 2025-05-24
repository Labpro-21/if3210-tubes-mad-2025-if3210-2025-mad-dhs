package com.tubes.purry.ui.player

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tubes.purry.R
import com.tubes.purry.databinding.FragmentMiniPlayerBinding
import androidx.core.net.toUri
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.tubes.purry.MainActivity
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.LikedSong
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import kotlinx.coroutines.launch

class MiniPlayerFragment : Fragment() {
    private lateinit var viewModel: NowPlayingViewModel
    private lateinit var binding: FragmentMiniPlayerBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMiniPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContext = requireContext().applicationContext
        val likedSongDao = AppDatabase.getDatabase(appContext).LikedSongDao()
        val songDao = AppDatabase.getDatabase(appContext).songDao()

        val profileViewModelFactory = ProfileViewModelFactory(requireContext())
        val profileViewModel = ViewModelProvider(requireActivity(), profileViewModelFactory)[ProfileViewModel::class.java]

        val factory = NowPlayingViewModelFactory(requireActivity().application, likedSongDao, songDao, profileViewModel)
        viewModel = ViewModelProvider(requireActivity(), factory)[NowPlayingViewModel::class.java]

        viewModel.currSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding.textTitle.text = song.title
                binding.textArtist.text = song.artist

                when {
                    song.coverResId != null -> {
                        Glide.with(binding.root)
                            .load(song.coverResId)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.imageCover)
                    }
                    !song.coverPath.isNullOrEmpty() -> {
                        Glide.with(binding.root)
                            .load(song.coverPath.toUri())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.imageCover)
                    }
                    else -> {
                        Glide.with(binding.root)
                            .load(R.drawable.album_default) // 👈 fallback image
                            .into(binding.imageCover)
                    }
                }
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        viewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            if (isLiked) {
                binding.btnFavorite.setImageResource(R.drawable.ic_heart_filled)
            } else {
                binding.btnFavorite.setImageResource(R.drawable.ic_heart_outline)
            }
        }

        binding.btnFavorite.setOnClickListener {
            val currentSong = viewModel.currSong.value
            currentSong?.let { song ->
                viewModel.toggleLike(song)
            }
        }

        binding.btnPlayPause.setOnClickListener {
            viewModel.togglePlayPause()
        }

        var lastClickTime = 0L
        binding.root.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 800) return@setOnClickListener
            lastClickTime = now

            try {
                val navHostFragment = requireActivity()
                    .supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

                val navController = navHostFragment?.navController
                if (navController != null && navController.currentDestination?.id != R.id.songDetailFragment) {
                    navController.navigate(R.id.songDetailFragment)
                } else {
                    Log.e("MiniPlayerFragment", "NavHostFragment atau navController null")
                }

                if (navController?.currentDestination?.id != R.id.songDetailFragment) {
                    navController?.navigate(R.id.songDetailFragment)
                }
            } catch (e: Exception) {
                Log.e("MiniPlayerFragment", "Navigation error: ${e.message}")
            }
        }
    }
}