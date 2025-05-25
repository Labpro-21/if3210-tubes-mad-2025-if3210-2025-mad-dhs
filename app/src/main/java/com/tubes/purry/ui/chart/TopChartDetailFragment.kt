package com.tubes.purry.ui.chart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.OnlineSong
import com.tubes.purry.data.model.Song
import com.tubes.purry.data.model.toTemporarySong
import com.tubes.purry.databinding.FragmentTopChartDetailBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory
import com.tubes.purry.utils.DownloadUtils
import com.tubes.purry.utils.parseDuration
import com.tubes.purry.utils.SessionManager
import com.tubes.purry.PurrytifyApplication
import kotlinx.coroutines.launch
import java.util.UUID

class TopChartDetailFragment : Fragment() {

    private var _binding: FragmentTopChartDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager

    private var currentSongList: List<OnlineSong> = emptyList()

    private lateinit var adapter: OnlineSongListAdapter

    private val profileViewModel: ProfileViewModel by activityViewModels {
        ProfileViewModelFactory(requireActivity().application)
    }

    private lateinit var nowPlayingViewModel: NowPlayingViewModel

    private val chartViewModel: ChartViewModel by viewModels {
        ChartViewModelFactory()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopChartDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nowPlayingViewModel = (requireActivity().application as PurrytifyApplication).nowPlayingViewModel

        val isGlobal = arguments?.getBoolean("isGlobal", true) ?: true
        profileViewModel.profileData.observe(viewLifecycleOwner) { profile ->
            val countryCode = if (!isGlobal) profile?.location ?: "ID" else null
            val countryName = if (!isGlobal) getCountryName(countryCode) else "GLOBAL"
            val chartTitle = "Top 50 $countryName"

            binding.tvChartTitle.text = chartTitle
            binding.tvChartDescription.text = "Your daily update of the most played tracks right now - $chartTitle"

            android.util.Log.d("TopChartDetail", "Country Code used: $countryCode")
            chartViewModel.fetchSongs(isGlobal, countryCode)
        }

        val coverRes = if (isGlobal) R.drawable.cov_playlist_global else R.drawable.cov_playlist_around
        sessionManager = SessionManager(requireContext())

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        Glide.with(this).load(coverRes).into(binding.ivChartCover)

        adapter = OnlineSongListAdapter(
            songs = emptyList(),
            onClick = { clickedOnlineSong ->
                val tempList = currentSongList.map { it.toTemporarySong() }
                val clickedSong = clickedOnlineSong.toTemporarySong()
                nowPlayingViewModel.setQueueFromClickedSong(clickedSong, tempList, requireContext())
            },
            onDownloadClick = { song ->
                val context = requireContext()
                adapter.markAsDownloading(song.url)

                DownloadUtils.downloadSong(
                    context = context,
                    onlineSong = song,
                    onComplete = { file ->
                        if (file != null) {
                            Toast.makeText(context, "Berhasil disimpan ke: ${file.absolutePath}", Toast.LENGTH_SHORT).show()

                            val localSong = Song(
                                id = UUID.randomUUID().toString(),
                                title = song.title,
                                artist = song.artist,
                                filePath = file.absolutePath,
                                coverPath = song.artwork,
                                duration = parseDuration(song.duration),
                                isLiked = false
                            )
                            lifecycleScope.launch {
                                AppDatabase.getDatabase(context).songDao().insert(localSong)
                            }
                            adapter.markAsDownloaded(song.url)
                        } else {
                            Toast.makeText(context, "Gagal download lagu", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        )

        var isShuffleMode = false

        binding.btnShuffle.setOnClickListener {
            isShuffleMode = !isShuffleMode
            val color = if (isShuffleMode) R.color.green else android.R.color.white
            binding.btnShuffle.setColorFilter(resources.getColor(color, null))
        }

        binding.btnPlay.setOnClickListener {
            val queue = if (isShuffleMode) currentSongList.shuffled() else currentSongList
            val songList = queue.map { it.toTemporarySong() }
            val firstSong = songList.firstOrNull()
            if (firstSong != null) {
                nowPlayingViewModel.setQueueFromClickedSong(firstSong, songList, requireContext())
            } else {
                Toast.makeText(requireContext(), "No songs to play", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownloadAll.setOnClickListener {
            val context = requireContext()
            for (song in currentSongList) {
                DownloadUtils.downloadSong(context, song) { file ->
                    if (file != null) {
                        Toast.makeText(context, "Unduh selesai: ${song.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Toast.makeText(context, "Mengunduh semua lagu...", Toast.LENGTH_SHORT).show()
        }

        binding.rvChartSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChartSongs.adapter = adapter

        chartViewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongList = songs
            adapter.updateSongs(songs)
            adapter.checkDownloadedStatus(requireContext())
        }

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun getCountryName(code: String?): String {
        return try {
            val locale = java.util.Locale("", code ?: "")
            locale.displayCountry.ifBlank { "Your Country" }
        } catch (e: Exception) {
            "Your Country"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
