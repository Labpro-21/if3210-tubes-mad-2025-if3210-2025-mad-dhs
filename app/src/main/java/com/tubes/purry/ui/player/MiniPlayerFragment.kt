package com.tubes.purry.ui.player

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
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory

class MiniPlayerFragment : Fragment() {
    private lateinit var viewModel: NowPlayingViewModel
    private var _binding: FragmentMiniPlayerBinding? = null // Nullable
    private val binding get() = _binding!! // Non-null accessor

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiniPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContext = requireContext().applicationContext
        val db = AppDatabase.getDatabase(appContext) // Get DB instance
        val likedSongDao = db.LikedSongDao()
        val songDao = db.songDao()

        // Use ProfileViewModelFactory correctly
        val profileFactory = ProfileViewModelFactory(appContext)
        val profileViewModel = ViewModelProvider(requireActivity(), profileFactory)[ProfileViewModel::class.java]

        val factory = NowPlayingViewModelFactory(likedSongDao, songDao, profileViewModel, appContext) // Pass AppContext
        viewModel = ViewModelProvider(requireActivity(), factory)[NowPlayingViewModel::class.java]

        // Make title scroll
        binding.textTitle.isSelected = true


        viewModel.currSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding.textTitle.text = song.title
                binding.textArtist.text = song.artist
                binding.root.visibility = View.VISIBLE // Show mini player if there's a song

                when {
                    song.coverResId != null -> Glide.with(this).load(song.coverResId).diskCacheStrategy(DiskCacheStrategy.ALL).error(R.drawable.album_default).into(binding.imageCover)
                    !song.coverPath.isNullOrEmpty() -> Glide.with(this).load(song.coverPath.toUri()).diskCacheStrategy(DiskCacheStrategy.ALL).error(R.drawable.album_default).into(binding.imageCover)
                    else -> Glide.with(this).load(R.drawable.album_default).into(binding.imageCover)
                }
            } else {
                binding.root.visibility = View.GONE // Hide if no song
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

        // Observe active audio output
        viewModel.activeAudioOutputInfo.observe(viewLifecycleOwner) { outputInfo ->
            outputInfo?.let {
                binding.ivMiniAudioOutputIcon.setImageResource(it.iconResId)
                // Optionally set content description for accessibility
                binding.ivMiniAudioOutputIcon.contentDescription = getString(R.string.playing_on, it.name)
            }
        }


        binding.btnFavorite.setOnClickListener {
            viewModel.currSong.value?.let { song -> viewModel.toggleLike(song) }
        }

        binding.btnPlayPause.setOnClickListener { viewModel.togglePlayPause() }

        binding.root.setOnClickListener {
            val navHostFragment = requireActivity().supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? androidx.navigation.fragment.NavHostFragment
            navHostFragment?.navController?.navigate(R.id.songDetailFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure mini-player visibility is correct based on current song
        if (viewModel.currSong.value == null) {
            binding.root.visibility = View.GONE
        } else {
            binding.root.visibility = View.VISIBLE
        }
        viewModel.updateActiveAudioOutput() // Refresh on resume
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear binding
    }
}