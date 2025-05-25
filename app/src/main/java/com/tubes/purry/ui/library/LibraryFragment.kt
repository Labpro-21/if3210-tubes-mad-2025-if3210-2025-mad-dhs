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
import com.tubes.purry.PurrytifyApplication

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var songListAdapter: SongListAdapter
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }
    private lateinit var nowPlayingViewModel: NowPlayingViewModel
    private lateinit var sessionManager: SessionManager
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nowPlayingViewModel = (requireActivity().application as PurrytifyApplication).nowPlayingViewModel
        sessionManager = SessionManager(requireContext())

        setupRecyclerView()
        setupSearchBar()
        setupFilterButtons()
        observeSongs()

        val userId = sessionManager.getUserId()
        if (userId != null) {
            observeLikedSongs(userId)
        }

        nowPlayingViewModel.currSong.observe(viewLifecycleOwner) {
            applyFilters()
        }
        nowPlayingViewModel.isLiked.observe(viewLifecycleOwner) {
            applyFilters()
        }

        enableSwipeToAddToQueue(binding.rvLibrarySongs, songListAdapter, nowPlayingViewModel)

        binding.btnAddSong.setOnClickListener {
            showAddSongBottomSheet()
        }
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
        viewModel.librarySongs.observe(viewLifecycleOwner) { songs ->
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.searchBarLibrary.text?.clear()
            binding.btnClearSearch.visibility = View.GONE
        }

        binding.searchBarLibrary.setOnFocusChangeListener { _, hasFocus ->
            binding.btnClearSearch.visibility =
                if (hasFocus && binding.searchBarLibrary.text?.isNotEmpty() == true) View.VISIBLE else View.GONE
        }
    }

    private fun setupFilterButtons() {
        binding.btnAll.setOnClickListener {
            isShowingLikedOnly = false
            updateButtonAppearance()
            applyFilters()
        }

        binding.btnLiked.setOnClickListener {
            isShowingLikedOnly = true
            updateButtonAppearance()
            applyFilters()
        }

        updateButtonAppearance()
    }

    private fun updateButtonAppearance() {
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
        val filteredList = if (searchText.isNotEmpty()) {
            binding.btnClearSearch.visibility = View.VISIBLE
            baseList.filter { it.title.lowercase().contains(searchText) || it.artist.lowercase().contains(searchText) }
        } else {
            binding.btnClearSearch.visibility = View.GONE
            baseList
        }
        songListAdapter.submitList(filteredList)
        updateEmptyState(filteredList)
    }

    private fun updateEmptyState(songs: List<Song>) {
        binding.emptyLibraryState.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmptyLibrary.text = when {
            songs.isNotEmpty() -> ""
            currentSearchQuery.isNotEmpty() -> getString(R.string.no_search_results)
            isShowingLikedOnly -> getString(R.string.no_liked_songs)
            else -> getString(R.string.empty_library)
        }
    }

    private fun onSongClicked(song: Song) {
        nowPlayingViewModel.setQueueFromClickedSong(song, allSongs, requireContext())
        nowPlayingViewModel.playSong(song, requireContext())
        viewModel.markAsPlayed(song)
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
