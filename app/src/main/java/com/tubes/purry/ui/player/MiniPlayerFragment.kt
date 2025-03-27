package com.tubes.purry.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.tubes.purry.R
import com.tubes.purry.databinding.FragmentMiniPlayerBinding

class MiniPlayerFragment : Fragment() {
    private lateinit var viewModel: NowPlayingViewModel
    private lateinit var binding: FragmentMiniPlayerBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMiniPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[NowPlayingViewModel::class.java]

        viewModel.currSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding.textTitle.text = song.title
                binding.textArtist.text = song.artist
                // Load cover image tadi udah bisa cuman masi kureng
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        binding.btnPlayPause.setOnClickListener {
            viewModel.togglePlayPause()
        }

        binding.root.setOnClickListener {
            findNavController().navigate(R.id.action_global_nowPlayingFragment)
        }
    }
}
