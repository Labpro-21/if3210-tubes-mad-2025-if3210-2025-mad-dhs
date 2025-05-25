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
import androidx.navigation.fragment.navArgs
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.FragmentSongDetailBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.player.PlayerController
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.utils.formatDuration
import androidx.navigation.fragment.findNavController
import com.tubes.purry.utils.previewAndShareQrCode


class SongDetailFragment : Fragment() {
    private lateinit var binding: FragmentSongDetailBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private val args: SongDetailFragmentArgs by navArgs()

    private val profileViewModel: ProfileViewModel by activityViewModels()
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

        val songId = args.songId
        if (songId <= 0) {
            Toast.makeText(requireContext(), "Invalid song ID", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val current = nowPlayingViewModel.currSong.value
        val currentId = current?.serverId ?: -1

        if (currentId != songId) {
            nowPlayingViewModel.fetchSongById(songId, requireContext())
        }

        nowPlayingViewModel.fetchSongById(songId, requireContext())

        nowPlayingViewModel.currSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                binding.tvTitle.text = it.title
                binding.tvTitle.isSelected = true
                binding.tvArtist.text = it.artist
                Glide.with(this)
                    .load(song.coverPath ?: song.coverResId ?: R.drawable.album_default)
                    .into(binding.ivCover)
                val durationInSeconds = it.duration
                binding.seekBar.max = durationInSeconds
                binding.tvDuration.text = formatDuration(durationInSeconds)
            }
        }

        nowPlayingViewModel.isPlaying.observe(viewLifecycleOwner) { //isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (it) R.drawable.ic_pause_btn
                else R.drawable.ic_play_btn
            )
        }

        nowPlayingViewModel.isShuffling.observe(viewLifecycleOwner) { //isShuffling ->
            val color = if (it) R.color.green else android.R.color.white
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

//        nowPlayingViewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
//            val resId = when (mode) {
//                NowPlayingViewModel.RepeatMode.ONE -> R.drawable.ic_repeat_one
//                else -> R.drawable.ic_repeat
//            }
//            val color = when (mode) {
//                NowPlayingViewModel.RepeatMode.NONE, null -> android.R.color.white
//                else -> R.color.green
//            }
//
//            binding.btnRepeat.setImageResource(resId)
//            binding.btnRepeat.setColorFilter(resources.getColor(color, null))
//        }

        binding.btnPlayPause.setOnClickListener { nowPlayingViewModel.togglePlayPause() }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnNext.setOnClickListener { nowPlayingViewModel.nextSong(requireContext()) }
        binding.btnPrev.setOnClickListener { nowPlayingViewModel.previousSong(requireContext()) }

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
            nowPlayingViewModel.currSong.value?.let {
                nowPlayingViewModel.toggleLike(it)
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

        binding.btnOptions.setOnClickListener {
            val popup = PopupMenu(requireContext(), it)
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
                    R.id.menu_share_qr -> {
                        nowPlayingViewModel.currSong.value?.let { song ->
                            if (!song.isLocal) {
                                previewAndShareQrCode(requireContext(), song.id, song.title, song.artist)
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
