package com.tubes.purry.ui.library

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tubes.purry.data.model.Song
import com.tubes.purry.databinding.FragmentAddSongBinding
import com.tubes.purry.utils.extractAudioMetadata
import java.util.UUID

class AddSongFragment : Fragment() {

    private lateinit var binding: FragmentAddSongBinding
    private var audioUri: Uri? = null
    private var duration: Int = 0
    private var imageUri: Uri? = null

    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imageUri = it
                binding.imgUpload.setImageURI(it)
            }
        }

    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                audioUri = it
                val metadata = extractAudioMetadata(requireContext(), it)
                binding.inputTitle.setText(metadata.title ?: "")
                binding.inputArtist.setText(metadata.artist ?: "")
                duration = metadata.duration
//                binding.textDuration.text = "Duration: ${duration / 1000} seconds"
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddSongBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgUpload.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnUploadFile.setOnClickListener {
            pickAudio.launch(arrayOf("audio/*"))
        }

        binding.btnSave.setOnClickListener {
            val title = binding.inputTitle.text.toString()
            val artist = binding.inputArtist.text.toString()
//            val duration = binding.textDuration.text.toString()
            val filePath = audioUri?.toString()?:""

            // Validasi
            if (audioUri == null) {
                Toast.makeText(requireContext(), "Please select an audio file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (title.isBlank()) {
                Toast.makeText(requireContext(), "Title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (artist.isBlank()) {
                Toast.makeText(requireContext(), "Artist cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val song = Song(
                id = UUID.randomUUID().toString(),
                title = title,
                artist = artist,
                filePath = filePath,
                resId = null,
                coverResId = null,
                coverPath = imageUri?.toString() ?: "",
                duration = duration,
                isLiked = false,
                isLocal = true
            )

            viewModel.insertSong(song)
            Toast.makeText(requireContext(), "Song added", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
