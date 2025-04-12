package com.tubes.purry.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tubes.purry.data.model.Song
import com.tubes.purry.databinding.FragmentEditSongBinding
import com.tubes.purry.utils.extractAudioMetadata
import java.util.*

class EditSongFragment : Fragment() {

    private lateinit var binding: FragmentEditSongBinding
    private val viewModel: SongViewModel by viewModels {
        SongViewModelFactory(requireContext())
    }

    private val args: EditSongFragmentArgs by navArgs()
    private var audioUri: Uri? = null
    private var imageUri: Uri? = null
    private var duration: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEditSongBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val song = args.song

        // Isi awal
        binding.inputTitle.setText(song.title)
        binding.inputArtist.setText(song.artist)
        imageUri = song.coverPath?.let { Uri.parse(it) }
        audioUri = song.filePath?.let { Uri.parse(it) }
        duration = song.duration

        imageUri?.let { binding.imgUpload.setImageURI(it) }

        binding.imgUpload.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnUploadFile.setOnClickListener {
            pickAudio.launch(arrayOf("audio/*"))
        }

        binding.btnSave.setOnClickListener {
            val updatedTitle = binding.inputTitle.text.toString()
            val updatedArtist = binding.inputArtist.text.toString()

            if (updatedTitle.isBlank() || updatedArtist.isBlank()) {
                Toast.makeText(requireContext(), "Title & artist can't be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedSong = song.copy(
                title = updatedTitle,
                artist = updatedArtist,
                filePath = audioUri?.toString() ?: song.filePath,
                coverPath = imageUri?.toString() ?: song.coverPath,
                duration = duration
            )

            viewModel.updateSong(updatedSong)
            Toast.makeText(requireContext(), "Song updated successfully", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
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
            binding.inputTitle.setText(metadata.title ?: "")
            binding.inputArtist.setText(metadata.artist ?: "")
            duration = metadata.duration
        }
    }
}
