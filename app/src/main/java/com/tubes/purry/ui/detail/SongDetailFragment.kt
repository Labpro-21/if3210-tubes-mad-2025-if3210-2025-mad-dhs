package com.tubes.purry.ui.detail

import android.os.Bundle
import com.bumptech.glide.Glide
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.databinding.FragmentSongDetailBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.utils.previewAndShareQrCode
import android.util.Log

class SongDetailFragment : Fragment() {
    private lateinit var binding: FragmentSongDetailBinding
    private var isDragging = false

    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val nowPlayingViewModel: NowPlayingViewModel by activityViewModels {
        NowPlayingViewModelFactory(
            requireActivity().application,
            AppDatabase.getDatabase(requireContext()).LikedSongDao(),
            AppDatabase.getDatabase(requireContext()).songDao(),
            profileViewModel
        )
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

        // Handle arguments dengan cara yang diperbaiki
        handleArguments()

        // Setup observers
        setupObservers()

        // Setup click listeners
        setupClickListeners()
    }

    private fun handleArguments() {
        arguments?.let { bundle ->
            Log.d("SongDetailFragment", "Bundle contents: ${bundle.keySet().joinToString()}")

            // Debug: Print semua values dalam bundle
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                Log.d("SongDetailFragment", "  $key = $value (${value?.javaClass?.simpleName})")
            }

            // Get basic parameters
            val songId = bundle.getString("songId")
            val isLocal = bundle.getBoolean("isLocal", false)
            val serverId = bundle.getInt("serverId", -1)

            Log.d("SongDetailFragment", "Parsed: songId=$songId, isLocal=$isLocal, serverId=$serverId")

            if (!songId.isNullOrEmpty()) {
                if (isLocal) {
                    // Local song
                    Log.d("SongDetailFragment", "Loading local song: $songId")
                    loadLocalSong(songId)
                } else {
                    // Server song - extract serverId from songId or use provided serverId
                    val serverIdToUse = if (serverId > 0) {
                        serverId
                    } else {
                        extractServerIdFromSongId(songId)
                    }

                    if (serverIdToUse != null && serverIdToUse > 0) {
                        Log.d("SongDetailFragment", "Loading server song with ID: $serverIdToUse")
                        loadServerSong(serverIdToUse)
                    } else {
                        showErrorAndReturn("ID lagu server tidak valid: $songId")
                    }
                }
            } else {
                // Fallback to legacy format
                handleLegacyFormat(bundle)
            }
        } ?: run {
            showErrorAndReturn("Tidak ada data lagu")
        }
    }

    private fun handleLegacyFormat(bundle: Bundle) {
        Log.d("SongDetailFragment", "Using legacy format")

        // Cek format lama dengan "id"
        val legacyId = bundle.getString("id")
        if (!legacyId.isNullOrEmpty()) {
            Log.d("SongDetailFragment", "Legacy ID: $legacyId")

            if (legacyId.startsWith("srv-")) {
                // Server song dengan format "srv-123"
                val serverId = extractServerIdFromSongId(legacyId)
                if (serverId != null) {
                    loadServerSong(serverId)
                } else {
                    showErrorAndReturn("Format ID server tidak valid: $legacyId")
                }
            } else {
                // Coba sebagai integer untuk server song lama
                val serverIdInt = legacyId.toIntOrNull()
                if (serverIdInt != null && serverIdInt > 0) {
                    loadServerSong(serverIdInt)
                } else {
                    // Assume it's local song UUID
                    loadLocalSong(legacyId)
                }
            }
        } else {
            showErrorAndReturn("ID lagu tidak ditemukan")
        }
    }

    private fun loadLocalSong(songId: String) {
        val current = nowPlayingViewModel.currSong.value
        if (current?.id != songId) {
            Log.d("SongDetailFragment", "Fetching local song: $songId")
            nowPlayingViewModel.fetchSongByUUID(songId, requireContext())
        } else {
            Log.d("SongDetailFragment", "Local song already loaded: $songId")
        }
    }

    private fun loadServerSong(serverId: Int) {
        val current = nowPlayingViewModel.currSong.value
        val expectedId = "srv-$serverId"

        if (current?.id != expectedId) {
            Log.d("SongDetailFragment", "Fetching server song: $serverId")
            nowPlayingViewModel.fetchSongById(serverId, requireContext())
        } else {
            Log.d("SongDetailFragment", "Server song already loaded: $serverId")
        }
    }

    private fun extractServerIdFromSongId(songId: String): Int? {
        return if (songId.startsWith("srv-")) {
            try {
                songId.removePrefix("srv-").toInt()
            } catch (e: NumberFormatException) {
                Log.e("SongDetailFragment", "Cannot extract serverId from: $songId", e)
                null
            }
        } else {
            null
        }
    }

    private fun showErrorAndReturn(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        findNavController().popBackStack()
    }

    private fun setupObservers() {
        nowPlayingViewModel.currSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                binding.tvTitle.text = it.title
                binding.tvTitle.isSelected = true
                binding.tvArtist.text = it.artist
                Glide.with(this)
                    .load(it.coverPath ?: it.coverResId ?: R.drawable.album_default)
                    .into(binding.ivCover)

                Log.d("SongDetailFragment", "Song loaded: ${it.title} (${it.id})")
            }
        }

        nowPlayingViewModel.songDuration.observe(viewLifecycleOwner) { duration ->
            if (duration > 0) {
                binding.seekBar.max = duration
                binding.tvDuration.text = nowPlayingViewModel.formatDurationMs(duration)
                Log.d("SongDetailFragment", "Duration set: $duration ms")
            }
        }

        nowPlayingViewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (!isDragging) {
                binding.seekBar.progress = position
                binding.tvCurrentTime.text = nowPlayingViewModel.formatDurationMs(position)
            }
        }

        nowPlayingViewModel.isPlaying.observe(viewLifecycleOwner) {
            binding.btnPlayPause.setImageResource(
                if (it) R.drawable.ic_pause_btn else R.drawable.ic_play_btn
            )
        }

        nowPlayingViewModel.isShuffling.observe(viewLifecycleOwner) {
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

        nowPlayingViewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            binding.btnFavorite.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
        }
    }

    private fun setupClickListeners() {
        binding.btnPlayPause.setOnClickListener { nowPlayingViewModel.togglePlayPause() }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnNext.setOnClickListener { nowPlayingViewModel.nextSong(requireContext()) }
        binding.btnPrev.setOnClickListener { nowPlayingViewModel.previousSong(requireContext()) }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = nowPlayingViewModel.formatDurationMs(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isDragging = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                isDragging = false
                seekBar?.progress?.let {
                    nowPlayingViewModel.seekTo(it)
                }
            }
        })

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
}