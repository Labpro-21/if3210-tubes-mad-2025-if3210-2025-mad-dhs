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
import androidx.navigation.fragment.findNavController
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
import com.tubes.purry.utils.previewAndShareQrCode
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tubes.purry.data.model.AudioDeviceType
import com.tubes.purry.PurrytifyApplication

class SongDetailFragment : Fragment() {
    private lateinit var binding: FragmentSongDetailBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false

    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val audioRoutingViewModel: AudioRoutingViewModel by activityViewModels()
    private lateinit var nowPlayingViewModel: NowPlayingViewModel

//    private val nowPlayingViewModel: NowPlayingViewModel by activityViewModels {
//        NowPlayingViewModelFactory(
//            requireActivity().application,
//            AppDatabase.getDatabase(requireContext()).LikedSongDao(),
//            AppDatabase.getDatabase(requireContext()).songDao(),
//            profileViewModel
//        )
//    }

    private val updateSeekRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && PlayerController.isPlaying()) {
                val currentPosition = PlayerController.getCurrentPosition()
                binding.seekBar?.progress = currentPosition
                binding.tvCurrentTime?.text = formatDuration(currentPosition)
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
        Log.d("SongDetailFragment", "🎵 onViewCreated masuk")
        super.onViewCreated(view, savedInstanceState)
        nowPlayingViewModel = (requireActivity().application as PurrytifyApplication).nowPlayingViewModel
        handleArguments()
        setupObservers()
        setupClickListeners()
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        val songId = arguments?.getInt("songId")
//        if (songId != null) {
//            nowPlayingViewModel.fetchSongById(songId)
//        }
//
//        setupObservers()
//        setupClickListeners()
//    }
//
//    private fun setupObservers() {
        // Existing observers
//        nowPlayingViewModel.currSong.observe(viewLifecycleOwner) { song ->
//            song?.let {
//                binding.tvTitle.text = it.title
//                binding.tvTitle.isSelected = true
//                binding.tvArtist.text = it.artist
//                Glide.with(this)
//                    .load(song.coverPath ?: song.coverResId ?: R.drawable.album_default)
//                    .into(binding.ivCover)
//                if (it.duration > 0) {
//                    binding.seekBar.max = it.duration
//                    binding.tvDuration.text = formatDuration(it.duration)
//                }
//            }
//        }
//
//        nowPlayingViewModel.isPlaying.observe(viewLifecycleOwner) { //isPlaying ->
//            binding.btnPlayPause.setImageResource(
//                if (it) R.drawable.ic_pause_btn
//                else R.drawable.ic_play_btn
//            )
//        }
//
//        nowPlayingViewModel.isShuffling.observe(viewLifecycleOwner) { //isShuffling ->
//            val color = if (it) R.color.green else android.R.color.white
//            binding.btnShuffle.setColorFilter(resources.getColor(color, null))
//        }
//
//        nowPlayingViewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
//            binding.btnRepeat.setImageResource(
//                when (mode) {
//                    NowPlayingViewModel.RepeatMode.ONE -> R.drawable.ic_repeat_one
//                    else -> R.drawable.ic_repeat
//                }
//            )
//            binding.btnRepeat.setColorFilter(resources.getColor(
//                if (mode == NowPlayingViewModel.RepeatMode.NONE) android.R.color.white else R.color.green,
//                null
//            ))
//        }

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

//        binding.btnPlayPause.setOnClickListener { nowPlayingViewModel.togglePlayPause() }
//        binding.btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
//        binding.btnNext.setOnClickListener { nowPlayingViewModel.nextSong(requireContext()) }
//        binding.btnPrev.setOnClickListener { nowPlayingViewModel.previousSong(requireContext()) }
//
//        binding.btnNext.setOnClickListener {
//            nowPlayingViewModel.nextSong(requireContext())
//        }
//
//        binding.btnPrev.setOnClickListener {
//            nowPlayingViewModel.previousSong(requireContext())
//        }
//
//        binding.btnFavorite.setOnClickListener {
//            nowPlayingViewModel.currSong.value?.let {
//                nowPlayingViewModel.toggleLike(it)
//            }
//        }
//
//        binding.btnShuffle.setOnClickListener {
//            nowPlayingViewModel.toggleShuffle()
//            Toast.makeText(
//                requireContext(),
//                "Shuffle ${if (nowPlayingViewModel.isShuffling.value == true) "ON" else "OFF"}",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//
//        binding.btnRepeat.setOnClickListener {
//            nowPlayingViewModel.toggleRepeat()
//            Toast.makeText(
//                requireContext(),
//                "Repeat mode: ${nowPlayingViewModel.repeatMode.value}",
//                Toast.LENGTH_SHORT
//            ).show()
//        }

        // Seek bar listener
//        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
//                if (fromUser) {
//                    binding.tvCurrentTime.text = formatDuration(progress)
//                }
//            }
//
//            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
//                isDragging = true
//            }
//
//            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
//                isDragging = false
//                seekBar?.progress?.let {
//                    PlayerController.seekTo(it)
//                }
//            }
//        })
//
//        // Observe the liked state
//        nowPlayingViewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
//            if (isLiked) {
//                binding.btnFavorite.setImageResource(R.drawable.ic_heart_filled)
//            } else {
//                binding.btnFavorite.setImageResource(R.drawable.ic_heart_outline)
//            }
//        }
//
//        binding.btnFavorite.setOnClickListener {
//            nowPlayingViewModel.currSong.value?.let {
//                nowPlayingViewModel.toggleLike(it)
//            }
//        }
//
//        binding.btnShuffle.setOnClickListener {
//            nowPlayingViewModel.toggleShuffle()
//            Toast.makeText(requireContext(), "Shuffle ${if (nowPlayingViewModel.isShuffling.value == true) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
//        }
//
//        binding.btnRepeat.setOnClickListener {
//            nowPlayingViewModel.toggleRepeat()
//            Toast.makeText(requireContext(), "Repeat mode: ${nowPlayingViewModel.repeatMode.value}", Toast.LENGTH_SHORT).show()
//        }
//
//        binding.btnOptions.setOnClickListener {
//            val popup = PopupMenu(requireContext(), it)
//            popup.menuInflater.inflate(R.menu.song_options_menu, popup.menu)
//            popup.setOnMenuItemClickListener { item ->
//                when (item.itemId) {
//                    R.id.menu_share -> {
//                        nowPlayingViewModel.currSong.value?.let { song ->
//                            if (!song.isLocal) {
//                                val serverId = song.id.removePrefix("srv-").toIntOrNull()
//                                if (serverId != null) {
//                                    val intent = Intent(Intent.ACTION_SEND).apply {
//                                        type = "text/plain"
//                                        putExtra(Intent.EXTRA_TEXT, "purrytify://song/$serverId")
//                                    }
//                                    startActivity(Intent.createChooser(intent, "Share via"))
//                                } else {
//                                    Toast.makeText(requireContext(), "ID server tidak valid", Toast.LENGTH_SHORT).show()
//                                }
//                            } else {
//                                Toast.makeText(requireContext(), "Lagu lokal tidak dapat dibagikan", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                        true
//                    }
//
//                    R.id.menu_share_qr -> {
//                        nowPlayingViewModel.currSong.value?.let { song ->
//                            if (!song.isLocal) {
//                                val serverId = song.id.removePrefix("srv-").toIntOrNull()
//                                if (serverId != null) {
//                                    previewAndShareQrCode(requireContext(), serverId, song.title, song.artist)
//                                } else {
//                                    Toast.makeText(requireContext(), "ID server tidak valid", Toast.LENGTH_SHORT).show()
//                                }
//                            } else {
//                                Toast.makeText(requireContext(), "Lagu lokal tidak dapat dibagikan", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                        true
//                    }
//
//                    else -> false
//                }
//            }
//            popup.show()
//        }

    private fun handleArguments() {
        arguments?.let { bundle ->
            val songId = bundle.getString("songId")
            val songIdInt = bundle.getInt("songIdInt", -1)
            val isLocal = bundle.getBoolean("isLocal", false)
            val serverId = bundle.getInt("serverId", -1)

            if (!songId.isNullOrEmpty()) {
                if (isLocal) {
                    loadLocalSong(songId)
                } else {
                    val serverIdToUse = if (serverId > 0) serverId else extractServerIdFromSongId(songId)
                    if (serverIdToUse != null && serverIdToUse > 0) {
                        loadServerSong(serverIdToUse)
                    } else {
                        showErrorAndReturn("ID lagu server tidak valid: $songId")
                    }
                }
            } else if (songIdInt > 0) {
                loadServerSong(songIdInt)
            } else {
                handleLegacyFormat(bundle)
            }
        } ?: run {
            showErrorAndReturn("Tidak ada data lagu")
        }
    }

    private fun handleLegacyFormat(bundle: Bundle) {
        val legacyId = bundle.getString("id")
        if (!legacyId.isNullOrEmpty()) {
            if (legacyId.startsWith("srv-")) {
                val serverId = extractServerIdFromSongId(legacyId)
                if (serverId != null) {
                    loadServerSong(serverId)
                } else {
                    showErrorAndReturn("Format ID server tidak valid: $legacyId")
                }
            } else {
                val serverIdInt = legacyId.toIntOrNull()
                if (serverIdInt != null && serverIdInt > 0) {
                    loadServerSong(serverIdInt)
                } else {
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
            nowPlayingViewModel.fetchSongByUUID(songId, requireContext())
        }
    }

    private fun loadServerSong(serverId: Int) {
        val current = nowPlayingViewModel.currSong.value
        val expectedId = "srv-$serverId"
        if (current?.id != expectedId) {
            nowPlayingViewModel.fetchSongById(serverId, requireContext())
        }
    }

    private fun extractServerIdFromSongId(songId: String): Int? {
        return if (songId.startsWith("srv-")) {
            songId.removePrefix("srv-").toIntOrNull()
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
                Log.d("SongDetailFragment", "🎧 Observed song: $song")
                binding.tvTitle?.text = it.title
                binding.tvTitle?.isSelected = true
                binding.tvArtist?.text = it.artist
                binding.ivCover?.let { it1 ->
                    Glide.with(this)
                        .load(it.coverPath ?: it.coverResId ?: R.drawable.album_default)
                        .into(it1)
                }
                // Set icon like berdasarkan properti lagu
                binding.btnFavorite?.setImageResource(
                    if (it.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
            }
        }

        nowPlayingViewModel.songDuration.observe(viewLifecycleOwner) { duration ->
            if (duration > 0) {
                binding.seekBar?.max = duration
                binding.tvDuration?.text = nowPlayingViewModel.formatDurationMs(duration)
            }
        }

        nowPlayingViewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (!isDragging) {
                binding.seekBar?.progress = position
                binding.tvCurrentTime?.text = nowPlayingViewModel.formatDurationMs(position)
            }
        }

        nowPlayingViewModel.isPlaying.observe(viewLifecycleOwner) {
            binding.btnPlayPause?.setImageResource(
                if (it) R.drawable.ic_pause_btn else R.drawable.ic_play_btn
            )
        }

        nowPlayingViewModel.isShuffling.observe(viewLifecycleOwner) {
            val color = if (it) R.color.green else android.R.color.white
            binding.btnShuffle?.setColorFilter(resources.getColor(color, null))
        }

        nowPlayingViewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            binding.btnRepeat?.setImageResource(
                when (mode) {
                    NowPlayingViewModel.RepeatMode.ONE -> R.drawable.ic_repeat_one
                    else -> R.drawable.ic_repeat
                }
            )
            binding.btnRepeat?.setColorFilter(resources.getColor(
                if (mode == NowPlayingViewModel.RepeatMode.NONE) android.R.color.white else R.color.green,
                null
            ))
        }

        nowPlayingViewModel.isLiked.observe(viewLifecycleOwner) { isLiked ->
            binding.btnFavorite?.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
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
        binding.btnPlayPause?.setOnClickListener { nowPlayingViewModel.togglePlayPause() }
        binding.btnBack?.setOnClickListener { findNavController().popBackStack() }
//        binding.btnBack?.setOnClickListener {
//            requireActivity().onBackPressedDispatcher.onBackPressed()
//        }
        binding.btnNext?.setOnClickListener { nowPlayingViewModel.nextSong(requireContext()) }
        binding.btnPrev?.setOnClickListener { nowPlayingViewModel.previousSong(requireContext()) }

        // NEW: Audio device selection button
        binding.btnAudioDevice?.setOnClickListener {
            showAudioDeviceBottomSheet()
        }

//        binding.btnOptions?.setOnClickListener {
//            showOptionsMenu(it)
//        }
        binding.seekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime?.text = nowPlayingViewModel.formatDurationMs(progress)
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

        binding.btnFavorite?.setOnClickListener {
            nowPlayingViewModel.currSong.value?.let {
                nowPlayingViewModel.toggleLike(it)
            }
        }

        binding.btnShuffle?.setOnClickListener {
            nowPlayingViewModel.toggleShuffle()
            Toast.makeText(requireContext(), "Shuffle ${if (nowPlayingViewModel.isShuffling.value == true) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        binding.btnRepeat?.setOnClickListener {
            nowPlayingViewModel.toggleRepeat()
            Toast.makeText(requireContext(), "Repeat mode: ${nowPlayingViewModel.repeatMode.value}", Toast.LENGTH_SHORT).show()
        }

        binding.btnOptions?.setOnClickListener {
            val popup = PopupMenu(requireContext(), it)
            popup.menuInflater.inflate(R.menu.song_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_share -> {
                        nowPlayingViewModel.currSong.value?.let { song ->
                            if (!song.isLocal) {
                                val serverId = song.id.removePrefix("srv-").toIntOrNull()
                                if (serverId != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "purrytify://song/$serverId")
                                    }
                                    startActivity(Intent.createChooser(intent, "Share via"))
                                } else {
                                    Toast.makeText(requireContext(), "ID server tidak valid", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(requireContext(), "Lagu lokal tidak dapat dibagikan", Toast.LENGTH_SHORT).show()
                            }
                        }
                        true
                    }

                    R.id.menu_share_qr -> {
                        nowPlayingViewModel.currSong.value?.let { song ->
                            if (!song.isLocal) {
                                val serverId = song.id.removePrefix("srv-").toIntOrNull()
                                if (serverId != null) {
                                    previewAndShareQrCode(requireContext(), serverId, song.title, song.artist)
                                } else {
                                    Toast.makeText(requireContext(), "ID server tidak valid", Toast.LENGTH_SHORT).show()
                                }
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

//    private fun showOptionsMenu(view: View) {
//        val popup = PopupMenu(requireContext(), view)
//        popup.menuInflater.inflate(R.menu.song_options_menu, popup.menu)
//        popup.setOnMenuItemClickListener { item ->
//            when (item.itemId) {
//                R.id.menu_share -> {
//                    nowPlayingViewModel.currSong.value?.let { song ->
//                        if (!song.isLocal) {
//                            val intent = Intent(Intent.ACTION_SEND).apply {
//                                type = "text/plain"
//                                putExtra(Intent.EXTRA_TEXT, "purrytify://song/${song.id}")
//                            }
//                            startActivity(Intent.createChooser(intent, "Share via"))
//                        } else {
//                            Toast.makeText(requireContext(), "Lagu lokal tidak dapat dibagikan", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                    true
//                }
//                else -> false
//            }
//        }
//        popup.show()
//    }
//
    private fun showAudioDeviceBottomSheet() {
        val bottomSheet = AudioDeviceBottomSheet.newInstance()
        bottomSheet.show(parentFragmentManager, AudioDeviceBottomSheet.TAG)
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

        binding.btnAudioDevice?.setImageResource(iconRes)
        binding.btnAudioDevice?.contentDescription = "Audio output: $deviceName"
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
