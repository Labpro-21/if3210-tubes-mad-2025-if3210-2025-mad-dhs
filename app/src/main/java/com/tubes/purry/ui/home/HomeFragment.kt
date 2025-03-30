package com.tubes.purry.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.tubes.purry.databinding.FragmentHomeBinding
import com.tubes.purry.data.model.Song

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }

    private lateinit var newSongsAdapter: SongCardAdapter
    private lateinit var recentSongsAdapter: SongListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        // TODO: arahin ke player atau tampilkan mini player
        Toast.makeText(requireContext(), "Play: ${song.title}", Toast.LENGTH_SHORT).show()

        // Tandai sebagai recently played
        viewModel.markAsPlayed(song)
    }
}
