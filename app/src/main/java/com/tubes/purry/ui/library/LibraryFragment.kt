package com.tubes.purry.ui.library

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tubes.purry.R
import com.tubes.purry.data.model.Song
import com.tubes.purry.databinding.FragmentLibraryBinding
import com.tubes.purry.ui.player.MiniPlayerFragment
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.utils.SessionManager

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var songListAdapter: SongListAdapter
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }
    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var sessionManager: SessionManager
    private var currentUserId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun setupRecyclerView() {
        songListAdapter = SongListAdapter(
            onClick = { song -> onSongClicked(song) },
            onEdit = { song -> onEditSong(song) },
            onDelete = { song -> onDeleteSong(song) }
        )

        binding.rvLibrarySongs.apply {
            adapter = songListAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeSongs() {
        viewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            songListAdapter.submitList(songs)
        }
    }

    private fun onSongClicked(song: Song) {
        nowPlayingViewModel.playSong(song, requireContext())
        viewModel.markAsPlayed(song)

        val fragmentManager = requireActivity().supportFragmentManager
        val existingFragment = fragmentManager.findFragmentById(R.id.miniPlayerContainer)

        if (existingFragment == null) {
            fragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
                .commit()
        }

        val container = requireActivity().findViewById<FrameLayout>(R.id.miniPlayerContainer)
        if (container.visibility != View.VISIBLE) {
            container.alpha = 0f
            container.visibility = View.VISIBLE
            container.animate().alpha(1f).setDuration(250).start()
        }
    }

    private fun onEditSong(song: Song) {
        val action = LibraryFragmentDirections.actionLibraryFragmentToEditSongFragment(song)
        findNavController().navigate(action)
    }

    private fun onDeleteSong(song: Song) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete \"${song.title}\"?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.deleteSong(song)
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nowPlayingViewModel = ViewModelProvider(requireActivity())[NowPlayingViewModel::class.java]
        sessionManager = SessionManager(requireContext())
        currentUserId = sessionManager.getUserId()

        setupRecyclerView()
        observeSongs()

        binding.btnAddSong.setOnClickListener {
            findNavController().navigate(R.id.action_libraryFragment_to_addSongFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}