package com.tubes.purry.ui.detail

import android.os.Bundle
import com.bumptech.glide.Glide
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.FragmentSongDetailBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.player.PlayerController
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.utils.formatDuration

class SongDetailFragment : Fragment() {
    private lateinit var binding: FragmentSongDetailBinding
    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false

    private val profileViewModel: ProfileViewModel by activityViewModels()

    private val updateSeekRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && PlayerController.isPlaying()) {
                val currentPosition = PlayerController.getCurrentPosition()
                binding.seekBar.progress = currentPosition
                binding.tvCurrentTime.text = formatDuration(currentPosition)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSongDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext().applicationContext
        val db = AppDatabase.getDatabase(context)
        val factory = NowPlayingViewModelFactory(db.LikedSongDao(), db.songDao(), profileViewModel)
        nowPlayingViewModel = ViewModelProvider(requireActivity(), factory)[NowPlayingViewModel::class.java]

        nowPlayingViewModel.currSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                binding.tvTitle.text = it.title
                binding.tvArtist.text = it.artist
                Glide.with(this)
                    .load(song.coverPath ?: song.coverResId ?: R.drawable.album_default)
                    .into(binding.ivCover)
                binding.tvDuration.text = formatDuration(it.duration)
                binding.seekBar.max = it.duration
            }
        }

        nowPlayingViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) com.tubes.purry.R.drawable.ic_pause_btn
                else com.tubes.purry.R.drawable.ic_play_btn
            )
        }

        nowPlayingViewModel.isShuffling.observe(viewLifecycleOwner) { isShuffling ->
            val color = if (isShuffling) R.color.green else android.R.color.white
            binding.btnShuffle.setColorFilter(resources.getColor(color, null))
        }

        nowPlayingViewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            val resId = when (mode) {
                NowPlayingViewModel.RepeatMode.NONE -> R.drawable.ic_repeat
                NowPlayingViewModel.RepeatMode.ALL -> R.drawable.ic_repeat
                NowPlayingViewModel.RepeatMode.ONE -> R.drawable.ic_repeat_one
                null -> R.drawable.ic_repeat
            }
            val color = when (mode) {
                NowPlayingViewModel.RepeatMode.NONE, null -> android.R.color.white
                NowPlayingViewModel.RepeatMode.ALL, NowPlayingViewModel.RepeatMode.ONE -> R.color.green
            }

            binding.btnRepeat.setImageResource(resId)
            binding.btnRepeat.setColorFilter(resources.getColor(color, null))
        }

        binding.btnPlayPause.setOnClickListener {
            nowPlayingViewModel.togglePlayPause()
        }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatDuration(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isDragging = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                isDragging = false
                seekBar?.progress?.let {
                    PlayerController.seekTo(it)
                }
            }
        })

        // Observe the liked state
        nowPlayingViewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            if (isLiked) {
                binding.btnFavorite.setImageResource(R.drawable.ic_heart_filled)
            } else {
                binding.btnFavorite.setImageResource(R.drawable.ic_heart_outline)
            }
        }

        binding.btnFavorite.setOnClickListener {
            val currentSong = nowPlayingViewModel.currSong.value
            currentSong?.let { song ->
                nowPlayingViewModel.toggleLike(song)
            }
        }

        binding.btnShuffle.setOnClickListener {
            nowPlayingViewModel.toggleShuffle()
            Toast.makeText(requireContext(), "Shuffle ${if (nowPlayingViewModel.isShuffling.value == true) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        binding.btnRepeat.setOnClickListener {
            nowPlayingViewModel.toggleRepeat()
            Toast.makeText(requireContext(), "Repeat mode: ${nowPlayingViewModel.repeatMode.value}", Toast.LENGTH_SHORT).show()
        }

        binding.btnNext.setOnClickListener {
            nowPlayingViewModel.nextSong(requireContext())
        }

        binding.btnPrev.setOnClickListener {
            nowPlayingViewModel.previousSong(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateSeekRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekRunnable)
    }
}
