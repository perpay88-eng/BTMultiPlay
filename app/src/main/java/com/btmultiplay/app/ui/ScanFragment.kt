package com.btmultiplay.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.btmultiplay.app.R
import com.btmultiplay.app.bluetooth.BtDeviceInfo
import com.btmultiplay.app.bluetooth.ConnectionState
import com.btmultiplay.app.databinding.FragmentScanBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: DeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DeviceAdapter(
            onConnect = { device -> connectDevice(device) },
            onDisconnect = { address -> viewModel.disconnectDevice(address) }
        )

        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@ScanFragment.adapter
        }

        binding.btnScan.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isScanning) viewModel.stopScan() else viewModel.startScan()
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update scan button
                    binding.btnScan.text = if (state.isScanning) "Stop Scan" else "Scan for Speakers"
                    binding.btnScan.setIconResource(
                        if (state.isScanning) R.drawable.ic_stop else R.drawable.ic_bluetooth_search
                    )

                    // Show/hide scanning indicator
                    binding.progressScanning.visibility =
                        if (state.isScanning) View.VISIBLE else View.GONE

                    // Empty state
                    binding.tvEmpty.visibility =
                        if (state.scanResults.isEmpty() && !state.isScanning) View.VISIBLE else View.GONE

                    // Update adapter with connection states merged
                    val enriched = state.scanResults.map { device ->
                        val connState = state.deviceStates[device.address] ?: ConnectionState.DISCONNECTED
                        device.copy(
                            connectionState = connState,
                            isActiveOutput = state.connectedDevices.containsKey(device.address)
                        )
                    }
                    adapter.submitList(enriched)

                    // Capability banner
                    state.audioCapability?.let { cap ->
                        binding.tvCapabilityBanner.text = if (cap.supportsDualAudio) {
                            "✓ This device supports simultaneous multi-speaker audio"
                        } else {
                            "⚠ Single audio output — connect multiple speakers for sequential switching"
                        }
                        binding.tvCapabilityBanner.setBackgroundResource(
                            if (cap.supportsDualAudio) R.color.banner_success else R.color.banner_warning
                        )
                    }
                }
            }
        }
    }

    private fun connectDevice(device: BtDeviceInfo) {
        viewModel.connectDevice(device)
        Snackbar.make(binding.root, "Connecting to ${device.displayName}…", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
