package com.tubes.purry.ui.detail

import android.os.Bundle
import com.bumptech.glide.Glide
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.FragmentSongDetailBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.player.PlayerController
import com.tubes.purry.ui.player.AudioRoutingViewModel
import com.tubes.purry.ui.player.AudioDeviceBottomSheet
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.utils.formatDuration
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tubes.purry.data.model.AudioDeviceType

class SongDetailFragment : Fragment() {

    private lateinit var binding: FragmentSongDetailBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false

    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val audioRoutingViewModel: AudioRoutingViewModel by activityViewModels()

    private val nowPlayingViewModel: NowPlayingViewModel by activityViewModels {
        NowPlayingViewModelFactory(
            requireActivity().application,
            AppDatabase.getDatabase(requireContext()).LikedSongDao(),
            AppDatabase.getDatabase(requireContext()).songDao(),
            profileViewModel
        )
    }

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
        super.onViewCreated(view, savedInstanceState)

        val songId = arguments?.getInt("songId")
        if (songId != null) {
            nowPlayingViewModel.fetchSongById(songId)
        }

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        // Existing observers
        nowPlayingViewModel.currSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                binding.tvTitle.text = it.title
                binding.tvTitle.isSelected = true
                binding.tvArtist.text = it.artist
                Glide.with(this)
                    .load(song.coverPath ?: song.coverResId ?: R.drawable.album_default)
                    .into(binding.ivCover)
                if (it.duration > 0) {
                    binding.seekBar.max = it.duration
                    binding.tvDuration.text = formatDuration(it.duration)
                }
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
            binding.btnShuffle.setColorFilter(resources.getColor(color, null))
        }

        nowPlayingViewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            binding.btnRepeat.setImageResource(
                when (mode) {
                    NowPlayingViewModel.RepeatMode.ONE -> R.drawable.ic_repeat_one
                    else -> R.drawable.ic_repeat
                }
            )
            binding.btnRepeat.setColorFilter(resources.getColor(
                if (mode == NowPlayingViewModel.RepeatMode.NONE) android.R.color.white else R.color.green,
                null
            ))
        }

        nowPlayingViewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            binding.btnFavorite.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
        }

        // NEW: Audio device observers
        audioRoutingViewModel.activeDevice.observe(viewLifecycleOwner) { device ->
            device?.let {
                updateAudioDeviceIndicator(it.type, it.name)
            }
        }

        audioRoutingViewModel.connectionError.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                audioRoutingViewModel.clearError()
            }
        }

        // CRITICAL: Setup MediaPlayer restart callback
        audioRoutingViewModel.onMediaPlayerRestartNeeded = { context, device ->
            Log.d("SongDetailFragment", "🔄 Restarting MediaPlayer for: ${device.name}")
            nowPlayingViewModel.restartMediaPlayerForAudioRouting(context)
        }
    }

    private fun setupClickListeners() {
        // Existing click listeners
        binding.btnPlayPause.setOnClickListener {
            nowPlayingViewModel.togglePlayPause()
        }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnNext.setOnClickListener {
            nowPlayingViewModel.nextSong(requireContext())
        }

        binding.btnPrev.setOnClickListener {
            nowPlayingViewModel.previousSong(requireContext())
        }

        binding.btnFavorite.setOnClickListener {
            nowPlayingViewModel.currSong.value?.let {
                nowPlayingViewModel.toggleLike(it)
            }
        }

        binding.btnShuffle.setOnClickListener {
            nowPlayingViewModel.toggleShuffle()
            Toast.makeText(
                requireContext(),
                "Shuffle ${if (nowPlayingViewModel.isShuffling.value == true) "ON" else "OFF"}",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnRepeat.setOnClickListener {
            nowPlayingViewModel.toggleRepeat()
            Toast.makeText(
                requireContext(),
                "Repeat mode: ${nowPlayingViewModel.repeatMode.value}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // NEW: Audio device selection button
        binding.btnAudioDevice.setOnClickListener {
            showAudioDeviceBottomSheet()
        }

        binding.btnOptions.setOnClickListener {
            showOptionsMenu(it)
        }

        // Seek bar listener
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
    }

    private fun showAudioDeviceBottomSheet() {
        val bottomSheet = AudioDeviceBottomSheet.newInstance()
        bottomSheet.show(parentFragmentManager, AudioDeviceBottomSheet.TAG)
    }

    private fun showOptionsMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.song_options_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_share -> {
                    nowPlayingViewModel.currSong.value?.let { song ->
                        if (!song.isLocal) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "purrytify://song/${song.id}")
                            }
                            startActivity(Intent.createChooser(intent, "Share via"))
                        } else {
                            Toast.makeText(requireContext(), "Lagu lokal tidak dapat dibagikan", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun updateAudioDeviceIndicator(deviceType: AudioDeviceType, deviceName: String) {
        val iconRes = when (deviceType) {
            AudioDeviceType.INTERNAL_SPEAKER -> R.drawable.ic_phone_speaker
            AudioDeviceType.WIRED_HEADPHONES -> R.drawable.ic_headphones_wired
            AudioDeviceType.BLUETOOTH_HEADPHONES -> R.drawable.ic_headphones_bluetooth
            AudioDeviceType.BLUETOOTH_SPEAKER -> R.drawable.ic_speaker_bluetooth
            AudioDeviceType.USB_AUDIO -> R.drawable.ic_usb_audio
            AudioDeviceType.UNKNOWN -> R.drawable.ic_audio_device_unknown
        }

        binding.btnAudioDevice.setImageResource(iconRes)
        binding.btnAudioDevice.contentDescription = "Audio output: $deviceName"
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateSeekRunnable)
        // Refresh audio devices when fragment becomes visible
        audioRoutingViewModel.refreshDevices()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekRunnable)
    }
}