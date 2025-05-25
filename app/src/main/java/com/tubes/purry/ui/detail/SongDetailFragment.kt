package com.tubes.purry.ui.detail

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import com.bumptech.glide.Glide
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.palette.graphics.Palette
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.FragmentSongDetailBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.player.PlayerController
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.formatDuration

class SongDetailFragment : Fragment() {
    private var _binding: FragmentSongDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false

    private val profileViewModel: ProfileViewModel by activityViewModels {
        ProfileViewModelFactory(requireContext().applicationContext)
    }

    private val updateSeekRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && PlayerController.isPlaying()) {
                val currentPosition = PlayerController.getCurrentPosition()
                _binding?.seekBar?.progress = currentPosition
                _binding?.tvCurrentTime?.text = formatDuration(currentPosition)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext().applicationContext
        val db = AppDatabase.getDatabase(context)
        val factory = NowPlayingViewModelFactory(db.LikedSongDao(), db.songDao(), profileViewModel, context)
        nowPlayingViewModel = ViewModelProvider(requireActivity(), factory)[NowPlayingViewModel::class.java]

        nowPlayingViewModel.currSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                binding.tvTitle.text = it.title
                binding.tvTitle.isSelected = true // For marquee
                binding.tvArtist.text = it.artist
                binding.tvDuration.text = formatDuration(it.duration)
                binding.seekBar.max = it.duration

                // Load album art and set dynamic background
                val coverResource = song.coverPath ?: song.coverResId ?: R.drawable.album_default
                Glide.with(this)
                    .asBitmap() // Important: Load as Bitmap for Palette
                    .load(coverResource)
                    .error(R.drawable.album_default) // Fallback for Glide error
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            binding.ivCover.setImageBitmap(resource)
                            setDynamicBackground(resource)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            binding.ivCover.setImageDrawable(placeholder)
                            // Optionally reset to a default background
                            binding.songDetailRootLayout.setBackgroundColor(Color.BLACK)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            super.onLoadFailed(errorDrawable)
                            binding.ivCover.setImageDrawable(errorDrawable ?: ContextCompat.getDrawable(requireContext(), R.drawable.album_default))
                            // Fallback to default black background on image load failure
                            binding.songDetailRootLayout.setBackgroundColor(Color.BLACK)
                        }
                    })
            }
        }

        nowPlayingViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause_btn
                else R.drawable.ic_play_btn
            )
        }

        nowPlayingViewModel.isShuffling.observe(viewLifecycleOwner) { isShuffling ->
            val color = if (isShuffling) R.color.green else android.R.color.white
            binding.btnShuffle.setColorFilter(ContextCompat.getColor(requireContext(), color))
        }

        nowPlayingViewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            val (resId, colorResId) = when (mode) {
                NowPlayingViewModel.RepeatMode.NONE -> R.drawable.ic_repeat to android.R.color.white
                NowPlayingViewModel.RepeatMode.ALL -> R.drawable.ic_repeat to R.color.green
                NowPlayingViewModel.RepeatMode.ONE -> R.drawable.ic_repeat_one to R.color.green
                null -> R.drawable.ic_repeat to android.R.color.white
            }
            binding.btnRepeat.setImageResource(resId)
            binding.btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), colorResId))
        }

        nowPlayingViewModel.activeAudioOutputInfo.observe(viewLifecycleOwner) { outputInfo ->
            outputInfo?.let {
                binding.tvAudioOutputName.text = it.name
                binding.ivAudioOutputIcon.setImageResource(it.iconResId)
            }
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
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) { isDragging = true }
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                isDragging = false
                seekBar?.progress?.let { PlayerController.seekTo(it) }
            }
        })

        nowPlayingViewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            binding.btnFavorite.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
        }

        binding.btnFavorite.setOnClickListener {
            nowPlayingViewModel.currSong.value?.let { nowPlayingViewModel.toggleLike(it) }
        }
        binding.btnShuffle.setOnClickListener {
            nowPlayingViewModel.toggleShuffle()
            Toast.makeText(requireContext(), "Shuffle ${if (nowPlayingViewModel.isShuffling.value == true) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
        binding.btnRepeat.setOnClickListener {
            nowPlayingViewModel.toggleRepeat()
            val modeText = when(nowPlayingViewModel.repeatMode.value) {
                NowPlayingViewModel.RepeatMode.NONE -> "Off"
                NowPlayingViewModel.RepeatMode.ALL -> "All"
                NowPlayingViewModel.RepeatMode.ONE -> "One"
                else -> "Off"
            }
            Toast.makeText(requireContext(), "Repeat mode: $modeText", Toast.LENGTH_SHORT).show()
        }
        binding.btnNext.setOnClickListener { nowPlayingViewModel.nextSong(requireContext()) }
        binding.btnPrev.setOnClickListener { nowPlayingViewModel.previousSong(requireContext()) }
    }

    private fun setDynamicBackground(bitmap: Bitmap) {
        Palette.from(bitmap).generate { palette ->
            palette?.let {
                // Get a vibrant or dominant color, fallback to a dark muted color or black
                val dominantColor = it.getDominantColor(Color.BLACK)
                val vibrantColor = it.getVibrantColor(dominantColor)
                val darkMutedColor = it.getDarkMutedColor(Color.BLACK)

                // Create a gradient: You can experiment with different combinations
                // For example, from vibrant to dark muted, or dominant to black
                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(vibrantColor, darkMutedColor) // Using vibrant and dark muted
                )
                binding.songDetailRootLayout.background = gradientDrawable
            } ?: run {
                // Fallback if palette generation fails
                binding.songDetailRootLayout.setBackgroundColor(Color.BLACK)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateSeekRunnable)
        nowPlayingViewModel.updateActiveAudioOutput()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}