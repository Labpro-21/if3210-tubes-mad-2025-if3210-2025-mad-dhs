package com.tubes.purry.ui.library

import android.annotation.SuppressLint
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
import com.tubes.purry.R
import com.tubes.purry.data.model.Song
import com.tubes.purry.databinding.FragmentAddSongBottomSheetBinding
import com.tubes.purry.utils.extractAudioMetadata
import com.tubes.purry.utils.parseDuration
import androidx.core.net.toUri

class EditSongBottomSheetFragment(private val song: Song) : BottomSheetDialogFragment() {

    private var _binding: FragmentAddSongBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SongViewModel

    private var audioUri: Uri? = null
    private var duration: String = ""
    private var imageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            imageUri = it
            requireContext().contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            binding.imgUpload.setImageURI(it)
        }
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            audioUri = it
            requireContext().contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val metadata = extractAudioMetadata(requireContext(), it)
            binding.inputTitle.setText(metadata.title ?: song.title)
            binding.inputArtist.setText(metadata.artist ?: song.artist)
            duration = metadata.duration ?: "0:00"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioUri = song.filePath?.toUri()
        imageUri = song.coverPath?.toUri()
        duration = song.duration.toString()

        binding.inputTitle.setText(song.title)
        binding.inputArtist.setText(song.artist)
        binding.textTitle.text = getString(R.string.edit_song)
        binding.btnSave.text = getString(R.string.save_update)
        song.coverPath?.let { binding.imgUpload.setImageURI(it.toUri()) }

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
            saveUpdatedSong()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun saveUpdatedSong() {
        val title = binding.inputTitle.text.toString()
        val artist = binding.inputArtist.text.toString()
        val filePath = audioUri?.toString() ?: song.filePath
        val finalDuration = parseDuration(duration)

        if (filePath.isNullOrBlank()) {
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

        val updatedSong = song.copy(
            title = title,
            artist = artist,
            filePath = filePath,
            coverPath = imageUri?.toString() ?: song.coverPath,
            duration = finalDuration
        )

        viewModel.updateSong(updatedSong)
        Toast.makeText(requireContext(), "Song updated", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
