package com.tubes.purry.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.MainActivity
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.ui.library.SongViewModel
import com.tubes.purry.ui.library.SongViewModelFactory
import com.tubes.purry.ui.library.SongCardAdapter
import com.tubes.purry.ui.library.SongListAdapter
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.databinding.FragmentHomeBinding
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.profile.ProfileViewModel


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

        val context = requireContext().applicationContext
        val db = AppDatabase.getDatabase(context)
        val likedSongDao = db.LikedSongDao()
        val songDao = db.songDao()

        // Get ProfileViewModel using default factory
        val profileViewModel: ProfileViewModel = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]

        // Use custom factory
        val factory = NowPlayingViewModelFactory(likedSongDao, songDao, profileViewModel)
        nowPlayingViewModel = ViewModelProvider(requireActivity(), factory)[NowPlayingViewModel::class.java]

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
        Log.d("HomeFragment", "Song clicked: ${song.title}")
        nowPlayingViewModel.playSong(song, requireContext())
        viewModel.markAsPlayed(song)

        (requireActivity() as MainActivity).showMiniPlayer()
    }
}
