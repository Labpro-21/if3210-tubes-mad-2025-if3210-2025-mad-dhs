package com.tubes.purry.ui.home

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.MainActivity
import com.tubes.purry.data.local.AppDatabase
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.ui.library.SongViewModel
import com.tubes.purry.ui.library.SongViewModelFactory
import com.tubes.purry.ui.library.SongCardAdapter
import com.tubes.purry.ui.library.SongListAdapter
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.databinding.FragmentHomeBinding
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.library.EditSongBottomSheetFragment
import com.tubes.purry.ui.player.MiniPlayerFragment
import androidx.core.graphics.toColorInt
import com.tubes.purry.utils.SessionManager


class HomeFragment : Fragment() {
    private lateinit var binding: FragmentHomeBinding
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }

    // Add recommendation ViewModel
    private lateinit var recommendationViewModel: RecommendationViewModel

    private var allSongs: List<Song> = emptyList()
    private lateinit var newSongsAdapter: SongCardAdapter
    private lateinit var recentSongsAdapter: SongListAdapter
    private lateinit var nowPlayingViewModel: NowPlayingViewModel

    private lateinit var sessionManager: SessionManager

    // Add recommendation adapters
    private lateinit var dailyPlaylistAdapter: RecommendationAdapter
    private lateinit var topMixesAdapter: RecommendationAdapter

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

        // Initialize SessionManager
        sessionManager = SessionManager(requireContext())

        // Initialize recommendation ViewModel
        recommendationViewModel = ViewModelProvider(this)[RecommendationViewModel::class.java]

        val profileViewModel: ProfileViewModel = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]
        val factory = NowPlayingViewModelFactory(likedSongDao, songDao, profileViewModel)
        nowPlayingViewModel = ViewModelProvider(requireActivity(), factory)[NowPlayingViewModel::class.java]

        setupAdapters()
        observeSongs()
        observeRecommendations()
        enableSwipeToAddToQueue(binding.rvRecentlyPlayed, recentSongsAdapter, nowPlayingViewModel)
        enableSwipeToAddToQueue(binding.rvDailyPlaylist, dailyPlaylistAdapter, nowPlayingViewModel)
        enableSwipeToAddToQueue(binding.rvTopMixes, topMixesAdapter, nowPlayingViewModel)
    }

    private fun setupAdapters() {
        newSongsAdapter = SongCardAdapter { song ->
            onSongClicked(song)
        }

        recentSongsAdapter = SongListAdapter(
            onClick = { song -> onSongClicked(song) },
            onEdit = { song -> showEditBottomSheet(song) },
            onDelete = { song -> confirmDelete(song) }
        )

        // Setup recommendation adapters
        dailyPlaylistAdapter = RecommendationAdapter { song ->
            onSongClicked(song)
        }

        topMixesAdapter = RecommendationAdapter { song ->
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

        // Setup recommendation RecyclerViews
        binding.rvDailyPlaylist.apply {
            adapter = dailyPlaylistAdapter
            layoutManager = LinearLayoutManager(context)
        }

        binding.rvTopMixes.apply {
            adapter = topMixesAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeRecommendations() {
        // Get current user ID (you'll need to implement this based on your profile system)
        val currentUserId = getCurrentUserId() // Implement this method

        recommendationViewModel.getDailyPlaylist(currentUserId).observe(viewLifecycleOwner) { songs ->
            dailyPlaylistAdapter.submitList(songs)
            // Show/hide daily playlist section based on availability
            binding.sectionDailyPlaylist.visibility = if (songs.isNotEmpty()) View.VISIBLE else View.GONE
        }

        recommendationViewModel.getTopMixes(currentUserId).observe(viewLifecycleOwner) { songs ->
            topMixesAdapter.submitList(songs)
            // Show/hide top mixes section based on availability
            binding.sectionTopMixes.visibility = if (songs.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun getCurrentUserId(): Int {
        // Try to get user ID from SessionManager
        val userId = sessionManager.getUserId()
            ?: sessionManager.getUserIdFromToken() // Fallback to token if direct ID not available
            ?: 1 // Default fallback for development/testing

        Log.d("HomeFragment", "Current user ID: $userId")
        return userId
    }


    private fun confirmDelete(song: Song) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete \"${song.title}\"?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.deleteSong(song)
                nowPlayingViewModel.removeFromQueue(song.id, requireContext())
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showEditBottomSheet(song: Song) {
        val editSheet = EditSongBottomSheetFragment(song)
        editSheet.show(childFragmentManager, "EditSongBottomSheet")
    }

    private fun observeSongs() {
        viewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            allSongs = songs
            newSongsAdapter.submitList(songs)
        }

        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            recentSongsAdapter.submitList(songs)
        }
    }

    private fun onSongClicked(song: Song) {
        Log.d("HomeFragment", "Song clicked: ${song.title}")
        nowPlayingViewModel.setQueueFromClickedSong(song, allSongs, requireContext())
        nowPlayingViewModel.playSong(song, requireContext())
        viewModel.markAsPlayed(song)

        val fragmentManager = requireActivity().supportFragmentManager

        // Check if MiniPlayerFragment is already attached
        val existingFragment = fragmentManager.findFragmentById(R.id.miniPlayerContainer)
        if (existingFragment == null) {
            fragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
                .commit()
        }

        // Make the container visible with fade-in if it's not already
        val container = requireActivity().findViewById<FrameLayout>(R.id.miniPlayerContainer)
        if (container.visibility != View.VISIBLE) {
            container.alpha = 0f
            container.visibility = View.VISIBLE
            container.animate().alpha(1f).setDuration(250).start()
        }

//        (requireActivity() as MainActivity).showMiniPlayer()
    }

    // Add swipe support for recommendation lists
    private fun enableSwipeToAddToQueue(
        recyclerView: RecyclerView,
        adapter: RecommendationAdapter,
        nowPlayingViewModel: NowPlayingViewModel
    ) {
        val paint = Paint().apply { color = "#4CAF50".toColorInt() }
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val song = adapter.getSongAt(position)
                nowPlayingViewModel.addToQueue(song, requireContext())
                adapter.notifyItemChanged(position)
                Toast.makeText(requireContext(), "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView: View = viewHolder.itemView
                    c.drawRect(
                        itemView.right.toFloat() + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        paint
                    )
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    // Add this overloaded function for SongListAdapter
    private fun enableSwipeToAddToQueue(
        recyclerView: RecyclerView,
        adapter: SongListAdapter,
        nowPlayingViewModel: NowPlayingViewModel
    ) {
        val paint = Paint().apply { color = "#4CAF50".toColorInt() }
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val song = adapter.getSongAt(position) // Make sure SongListAdapter has this method
                nowPlayingViewModel.addToQueue(song, requireContext())
                adapter.notifyItemChanged(position)
                Toast.makeText(requireContext(), "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView: View = viewHolder.itemView
                    c.drawRect(
                        itemView.right.toFloat() + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        paint
                    )
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

}
