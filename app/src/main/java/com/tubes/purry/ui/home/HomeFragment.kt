package com.tubes.purry.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
// import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.R
import com.tubes.purry.ui.library.SongViewModel
import com.tubes.purry.ui.library.SongViewModelFactory
import com.tubes.purry.ui.library.SongCardAdapter
import com.tubes.purry.ui.library.SongListAdapter
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.databinding.FragmentHomeBinding
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.player.MiniPlayerFragment


class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }

    private lateinit var newSongsAdapter: SongCardAdapter
    private lateinit var recentSongsAdapter: SongListAdapter

    private lateinit var nowPlayingViewModel: NowPlayingViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nowPlayingViewModel = ViewModelProvider(requireActivity())[NowPlayingViewModel::class.java]

        setupAdapters()
        observeSongs()
    }

    private fun setupAdapters() {
        newSongsAdapter = SongCardAdapter { song ->
            onSongClicked(song)
        }

        recentSongsAdapter = SongListAdapter { song ->
            onSongClicked(song)
        }

        binding.rvNewSongs.apply {
            adapter = newSongsAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.rvRecentlyPlayed.apply {
            adapter = recentSongsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeSongs() {
        viewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            newSongsAdapter.submitList(songs)
        }

        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            recentSongsAdapter.submitList(songs)
        }
    }

    private fun onSongClicked(song: Song) {
        nowPlayingViewModel.playSong(song, requireContext())
        viewModel.markAsPlayed(song) // Mark as recently played

        // Show the MiniPlayerFragment
        val container = requireActivity().findViewById<FrameLayout>(R.id.miniPlayerContainer)

        if (container.visibility != View.VISIBLE) {
            container.alpha = 0f
            container.visibility = View.VISIBLE
            container.animate().alpha(1f).setDuration(250).start()
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
            .commit()
    }
}
