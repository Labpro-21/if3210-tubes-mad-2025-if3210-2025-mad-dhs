package com.tubes.purry.ui.home

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.ui.library.SongViewModel
import com.tubes.purry.ui.library.SongViewModelFactory
import com.tubes.purry.ui.library.SongCardAdapter
import com.tubes.purry.ui.library.SongListAdapter
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.databinding.FragmentHomeBinding
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.library.EditSongBottomSheetFragment
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
            newSongsAdapter.submitList(songs)
            nowPlayingViewModel.setAllSongs(songs)
        }

        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            recentSongsAdapter.submitList(songs)
        }

    }

    private fun onSongClicked(song: Song) {
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
        val paint = Paint().apply { color = Color.parseColor("#4CAF50") }

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

}
