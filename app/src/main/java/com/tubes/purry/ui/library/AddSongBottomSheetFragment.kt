package com.tubes.purry.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tubes.purry.data.model.Song
import com.tubes.purry.databinding.FragmentAddSongBottomSheetBinding
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.utils.SessionManager
import com.tubes.purry.utils.extractAudioMetadata
import com.tubes.purry.utils.parseDuration
import java.util.UUID

class AddSongBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAddSongBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: SongViewModel
    private lateinit var nowPlayingViewModel: NowPlayingViewModel


    private var audioUri: Uri? = null
    private var duration: String = null.toString()
    private var imageUri: Uri? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    imageUri = it
                } catch (_: SecurityException) {}
                binding.imgUpload.setImageURI(it)
            }
        }

    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                audioUri = it
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val metadata = extractAudioMetadata(requireContext(), it)

                if (binding.inputTitle.text.isNullOrBlank() && !metadata.title.isNullOrBlank()) {
                    binding.inputTitle.setText(metadata.title)
                }

                if (binding.inputArtist.text.isNullOrBlank() && !metadata.artist.isNullOrBlank()) {
                    binding.inputArtist.setText(metadata.artist)
                }
                duration = metadata.duration ?: "0:00"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(requireContext())
        viewModel = ViewModelProvider(
            this,
            SongViewModelFactory(requireContext())
        )[SongViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddSongBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nowPlayingViewModel = ViewModelProvider(requireActivity())[NowPlayingViewModel::class.java]
        setupListeners()
    }

    private fun setupListeners() {
        binding.imgUpload.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }

        binding.btnUploadFile.setOnClickListener {
            pickAudio.launch(arrayOf("audio/*"))
        }

        binding.btnSave.setOnClickListener {
            saveNewSong()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun saveNewSong() {
        val title = binding.inputTitle.text.toString()
        val artist = binding.inputArtist.text.toString()
        val filePath = audioUri?.toString() ?: ""

        // Validation
        if (audioUri == null) {
            Toast.makeText(requireContext(), "Please select an audio file", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Title cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (artist.isBlank()) {
            Toast.makeText(requireContext(), "Artist cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = sessionManager.getUserId()

        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val song = Song(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            filePath = filePath,
            resId = null,
            coverResId = null,
            coverPath = imageUri?.toString() ?: "",
            duration = parseDuration(duration),
            isLiked = false,
            isLocal = true,
            uploadedBy = userId
        )

        viewModel.insertSong(song)
        nowPlayingViewModel.addToQueue(song, requireContext())

        Toast.makeText(requireContext(), "Song added", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}