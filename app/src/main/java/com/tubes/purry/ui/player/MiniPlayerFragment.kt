package com.tubes.purry.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tubes.purry.PurrytifyApplication
import com.tubes.purry.R
import com.tubes.purry.databinding.FragmentMiniPlayerBinding
import android.util.Log
import androidx.navigation.fragment.NavHostFragment

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

        viewModel = (requireActivity().application as PurrytifyApplication).nowPlayingViewModel

        viewModel.currSong.observe(viewLifecycleOwner) { song ->
            Log.d("MiniPlayerFragment", "currSong updated: ${song?.title}")
            if (song != null) {
                binding.root.visibility = View.VISIBLE
                binding.textTitle.text = song.title
                binding.textArtist.text = song.artist

                val image = when {
                    song.coverResId != null -> song.coverResId
                    !song.coverPath.isNullOrEmpty() -> song.coverPath.toUri()
                    else -> R.drawable.album_default
                }

                Glide.with(binding.root)
                    .load(image)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.imageCover)

                // ✅ Update icon like sesuai properti di currSong juga
                binding.btnFavorite.setImageResource(
                    if (song.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
            } else {
                binding.root.visibility = View.GONE
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        viewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            binding.btnFavorite.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
        }

        binding.btnFavorite.setOnClickListener {
            viewModel.currSong.value?.let { song ->
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
                val currentSong = viewModel.currSong.value
                if (currentSong == null) {
                    Log.w("MiniPlayerFragment", "No current song to show details for")
                    return@setOnClickListener
                }

                val navHostFragment = requireActivity()
                    .supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val navController = navHostFragment?.navController

                if (navController == null || navController.currentDestination?.id == R.id.songDetailFragment) {
                    Log.e("MiniPlayerFragment", "NavController not ready or already in detail")
                    return@setOnClickListener
                }

                val bundle = Bundle().apply {
                    putString("songId", currentSong.id)
                    putBoolean("isLocal", currentSong.isLocal)
                    putString("id", currentSong.id)
                    currentSong.serverId?.let { putInt("serverId", it) }
                }

                Log.d("MiniPlayerFragment", "Navigating to song detail for: ${currentSong.title}")
                navController.navigate(R.id.songDetailFragment, bundle)

            } catch (e: Exception) {
                Log.e("MiniPlayerFragment", "Navigation error: ${e.message}", e)
            }
        }
    }

}
