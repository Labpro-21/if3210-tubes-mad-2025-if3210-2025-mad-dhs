package com.tubes.purry.ui.library

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.data.model.Song
import com.tubes.purry.databinding.FragmentLibraryBinding
import com.tubes.purry.ui.player.MiniPlayerFragment
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.utils.SessionManager
import androidx.core.graphics.toColorInt

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var songListAdapter: SongListAdapter
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }
    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var sessionManager: SessionManager
//    private var currentUserId: Int? = null
    private var allSongs: List<Song> = emptyList()
    private var likedSongs: List<Song> = emptyList()

    private var isShowingLikedOnly = false
    private var currentSearchQuery = ""

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
            allSongs = songs
            applyFilters()
        }
    }

    private fun observeLikedSongs(userId: Int) {
        viewModel.getLikedSongsByUser(userId).observe(viewLifecycleOwner) { liked ->
            likedSongs = liked
            applyFilters()
        }
    }


    private fun setupSearchBar() {
        binding.searchBarLibrary.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                applyFilters()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Clear search button
        binding.btnClearSearch.setOnClickListener {
            binding.searchBarLibrary.text?.clear()
            binding.btnClearSearch.visibility = View.GONE
        }

        // Only show clear button when search has text
        binding.searchBarLibrary.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.searchBarLibrary.text?.isNotEmpty() == true) {
                binding.btnClearSearch.visibility = View.VISIBLE
            } else if (!hasFocus && binding.searchBarLibrary.text?.isEmpty() == true) {
                binding.btnClearSearch.visibility = View.GONE
            }
        }
    }

    private fun setupFilterButtons() {
        // All songs button
        binding.btnAll.setOnClickListener {
            isShowingLikedOnly = false
            updateButtonAppearance()
            applyFilters()
        }

        // liked songs button
        binding.btnLiked.setOnClickListener {
            isShowingLikedOnly = true
            updateButtonAppearance()
            applyFilters()
        }

        updateButtonAppearance()
    }

    private fun updateButtonAppearance() {
        // Update the background color of the buttons based on selected state
        if (isShowingLikedOnly) {
            binding.btnAll.backgroundTintList = requireContext().getColorStateList(R.color.dark_gray)
            binding.btnLiked.backgroundTintList = requireContext().getColorStateList(R.color.green)
        } else {
            binding.btnAll.backgroundTintList = requireContext().getColorStateList(R.color.green)
            binding.btnLiked.backgroundTintList = requireContext().getColorStateList(R.color.dark_gray)
        }
    }

    private fun applyFilters() {
        val searchText = currentSearchQuery.trim().lowercase()

        val baseList = if (isShowingLikedOnly) likedSongs else allSongs
        var filteredList = baseList

        // apply search filter if text exists
        if (searchText.isNotEmpty()) {
            filteredList = filteredList.filter { song ->
                song.title.lowercase().contains(searchText) ||
                        song.artist.lowercase().contains(searchText)
            }

            binding.btnClearSearch.visibility = View.VISIBLE
        } else {
            binding.btnClearSearch.visibility = View.GONE
        }

        // Update the adapter and empty state
        songListAdapter.submitList(filteredList)
        updateEmptyState(filteredList)
    }

    private fun updateEmptyState(songs: List<Song>) {
        if (songs.isEmpty()) {
            binding.emptyLibraryState.visibility = View.VISIBLE

            // Different message depending on current state
            when {
                currentSearchQuery.isNotEmpty() -> {
                    binding.textEmptyLibrary.text = getString(R.string.no_search_results)
                }
                isShowingLikedOnly -> {
                    binding.textEmptyLibrary.text = getString(R.string.no_liked_songs)
                }
                else -> {
                    binding.textEmptyLibrary.text = getString(R.string.empty_library)
                }
            }
        } else {
            binding.emptyLibraryState.visibility = View.GONE
        }
    }

    private fun onSongClicked(song: Song) {
        nowPlayingViewModel.setQueueFromClickedSong(song, allSongs, requireContext())
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

    private fun showAddSongBottomSheet() {
        val addSongBottomSheet = AddSongBottomSheetFragment()
        addSongBottomSheet.show(childFragmentManager, "AddSongBottomSheet")
    }

    private fun onEditSong(song: Song) {
        val editSongBottomSheet = EditSongBottomSheetFragment(song)
        editSongBottomSheet.show(childFragmentManager, "AddSongBottomSheet")
    }

    private fun onDeleteSong(song: Song) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nowPlayingViewModel = ViewModelProvider(requireActivity())[NowPlayingViewModel::class.java]
        sessionManager = SessionManager(requireContext())

        setupRecyclerView()
        setupSearchBar()
        setupFilterButtons()
        observeSongs()

        val userId = sessionManager.getUserId()
        if (userId != null) {
            observeLikedSongs(userId)
        }

        enableSwipeToAddToQueue(binding.rvLibrarySongs, songListAdapter, nowPlayingViewModel)

        binding.btnAddSong.setOnClickListener {
            showAddSongBottomSheet()
        }
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


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}