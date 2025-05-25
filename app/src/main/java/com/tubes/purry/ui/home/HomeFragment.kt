package com.tubes.purry.ui.home

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.navigation.fragment.findNavController
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.ViewModelProvider
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
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import com.tubes.purry.data.model.ChartItem
import com.tubes.purry.ui.chart.ChartAdapter

import com.tubes.purry.data.model.RecommendationItem
import com.tubes.purry.data.model.RecommendationType
import com.tubes.purry.ui.recommendation.RecommendationDetailActivity
import com.tubes.purry.utils.SessionManager

class HomeFragment : Fragment() {
    private lateinit var binding: FragmentHomeBinding
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }
    private var allSongs: List<Song> = emptyList()
    private lateinit var newSongsAdapter: SongCardAdapter
    private lateinit var recentSongsAdapter: SongListAdapter
    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var chartAdapter: ChartAdapter
    private lateinit var recommendationAdapter: RecommendationAdapter
    private lateinit var sessionManager: SessionManager

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

        sessionManager = SessionManager(requireContext())

        // Get ProfileViewModel using default factory
        val profileViewModel: ProfileViewModel = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]

        // Use custom factory
        val factory = NowPlayingViewModelFactory(requireActivity().application, likedSongDao, songDao, profileViewModel)
        nowPlayingViewModel = ViewModelProvider(requireActivity(), factory)[NowPlayingViewModel::class.java]

        setupAdapters()
        setupChartSection()
        setupRecommendationSection()
        observeSongs()
        enableSwipeToAddToQueue(binding.rvRecentlyPlayed, recentSongsAdapter, nowPlayingViewModel)
    }

    private fun setupAdapters() {
        newSongsAdapter = SongCardAdapter { song ->
            onSongClicked(song)
        }

        recentSongsAdapter = SongListAdapter(
            onClick = { song -> onSongClicked(song) },
            onEdit = {song -> showEditBottomSheet(song)},
            onDelete = { song -> confirmDelete(song) }
        )

        binding.rvNewSongs.apply {
            adapter = newSongsAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.rvRecentlyPlayed.apply {
            adapter = recentSongsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun confirmDelete(song: Song) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete \"${song.title}\"?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.deleteSong(song)
                nowPlayingViewModel.removeFromQueue(song.id.toString(), requireContext())
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


    private fun setupChartSection() {
        val chartItems = listOf(
            ChartItem(
                title = "Top 50 Global",
                description = "Most played globally",
                imageRes = R.drawable.cov_playlist_global,
                isGlobal = true
            ),
            ChartItem(
                title = "Top 50 Country",
                description = "Most played in Your Country",
                imageRes = R.drawable.cov_playlist_around,
                isGlobal = false
            )
        )

        chartAdapter = ChartAdapter(chartItems) { item ->
            val navController = requireActivity()
                .supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment)
                ?.findNavController()

            navController?.navigate(
                R.id.topChartDetailFragment,
                bundleOf("isGlobal" to item.isGlobal)
            )

        }

        binding.rvCharts.apply {
            adapter = chartAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupRecommendationSection() {
        val userId = sessionManager.getUserId()
        val userName = "User" // You can get this from your user profile

        val recommendationItems = listOf(
            RecommendationItem(
                id = "liked_songs_mix",
                title = "Liked Songs Mix",
                description = "Based on your favorites",
                imageRes = R.drawable.cov_liked_mix,
                type = RecommendationType.LIKED_SONGS_MIX
            ),
            RecommendationItem(
                id = "discovery_mix",
                title = "Discover Weekly",
                description = "New music for you",
                imageRes = R.drawable.cov_discover_weekly,
                type = RecommendationType.DISCOVERY_MIX
            ),
            RecommendationItem(
                id = "recently_played_mix",
                title = "On Repeat",
                description = "Songs you've been playing",
                imageRes = R.drawable.cov_on_repeat,
                type = RecommendationType.RECENTLY_PLAYED_MIX
            )
        )

        recommendationAdapter = RecommendationAdapter(recommendationItems) { item ->
            val intent = Intent(requireContext(), RecommendationDetailActivity::class.java)
            intent.putExtra("recommendation_type", item.type.name)
            intent.putExtra("title", item.title)
            intent.putExtra("description", item.description)
            intent.putExtra("image_res", item.imageRes)
            startActivity(intent)
        }

        binding.rvRecommendations.apply {
            adapter = recommendationAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }
    }
}
