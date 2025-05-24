package com.tubes.purry.ui.chart

import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.toTemporarySong
import com.tubes.purry.databinding.FragmentTopChartDetailBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory

class TopChartDetailFragment : Fragment() {

    private var _binding: FragmentTopChartDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: OnlineSongListAdapter
    private val nowPlayingViewModel: NowPlayingViewModel by activityViewModels {
        NowPlayingViewModelFactory(
            requireActivity().application,
            AppDatabase.getDatabase(requireContext()).LikedSongDao(),
            AppDatabase.getDatabase(requireContext()).songDao(),
            ViewModelProvider(requireActivity(), ProfileViewModelFactory(requireContext()))[ProfileViewModel::class.java]
        )
    }

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
        val isGlobal = arguments?.getBoolean("isGlobal", true) ?: true
        val chartTitle = if (isGlobal) "Top 50 GLOBAL" else "Top 50 INDONESIA"
        val coverRes = if (isGlobal) R.drawable.cov_top50_global else R.drawable.cov_top50_id

        binding.tvChartTitle.text = chartTitle
        binding.tvChartDescription.text = "Your daily update of the most played tracks right now - $chartTitle"
        Glide.with(this).load(coverRes).into(binding.ivChartCover)

        adapter = OnlineSongListAdapter(
            songs = emptyList(),
            onClick = { onlineSong ->
                val tempSong = onlineSong.toTemporarySong()
                nowPlayingViewModel.setQueueFromClickedSong(tempSong, listOf(tempSong), requireContext())
            },
            onDownloadClick = { song ->
                Toast.makeText(requireContext(), "Download: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvChartSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChartSongs.adapter = adapter

        chartViewModel.songs.observe(viewLifecycleOwner) { songs ->
            adapter.updateSongs(songs)
        }

        val countryCode = if (!isGlobal) "ID" else null
        chartViewModel.fetchSongs(isGlobal, countryCode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
