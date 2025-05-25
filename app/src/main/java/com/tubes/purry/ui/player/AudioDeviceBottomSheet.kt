package com.tubes.purry.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tubes.purry.R
import com.tubes.purry.databinding.BottomSheetAudioDeviceBinding
import com.tubes.purry.data.model.AudioDevice

class AudioDeviceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAudioDeviceBinding? = null
    private val binding get() = _binding!!

    private val audioRoutingViewModel: AudioRoutingViewModel by activityViewModels()
    private lateinit var audioDeviceAdapter: AudioDeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAudioDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        binding.btnRefresh.setOnClickListener {
            audioRoutingViewModel.refreshDevices()
            binding.progressBar.visibility = View.VISIBLE
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        audioDeviceAdapter = AudioDeviceAdapter { device ->
            selectDevice(device)
        }

        binding.rvAudioDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = audioDeviceAdapter
        }
    }

    private fun observeViewModel() {
        audioRoutingViewModel.availableDevices.observe(viewLifecycleOwner) { devices ->
            audioDeviceAdapter.submitList(devices)
            binding.progressBar.visibility = View.GONE

            if (devices.isEmpty()) {
                binding.tvNoDevices.visibility = View.VISIBLE
                binding.rvAudioDevices.visibility = View.GONE
            } else {
                binding.tvNoDevices.visibility = View.GONE
                binding.rvAudioDevices.visibility = View.VISIBLE
            }
        }

        audioRoutingViewModel.connectionError.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                audioRoutingViewModel.clearError()
            }
        }
    }

    private fun selectDevice(device: AudioDevice) {
        if (!device.isConnected) {
            Toast.makeText(
                requireContext(),
                "Device ${device.name} is not connected",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val success = audioRoutingViewModel.selectAudioDevice(device)
        if (success) {
            Toast.makeText(
                requireContext(),
                "Switched to ${device.name}",
                Toast.LENGTH_SHORT
            ).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AudioDeviceBottomSheet"

        fun newInstance() = AudioDeviceBottomSheet()
    }
}