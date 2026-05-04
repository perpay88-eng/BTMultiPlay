package com.btmultiplay.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.btmultiplay.app.audio.PlaybackMode
import com.btmultiplay.app.databinding.FragmentConnectedBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ConnectedFragment : Fragment() {

    private var _binding: FragmentConnectedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ConnectedDeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ConnectedDeviceAdapter(
            onDisconnect = { address ->
                viewModel.disconnectDevice(address)
                Snackbar.make(binding.root, "Disconnecting…", Snackbar.LENGTH_SHORT).show()
            },
            onForget = { address, name ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Forget $name?")
                    .setMessage("This device will be removed from your saved list and won't auto-reconnect.")
                    .setPositiveButton("Forget") { _, _ -> viewModel.forgetDevice(address) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onSetAutoReconnect = { address, enabled ->
                viewModel.setAutoReconnect(address, enabled)
            }
        )

        binding.rvConnected.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@ConnectedFragment.adapter
        }

        binding.btnTestSync.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.syncStatus?.isPlaying == true) {
                viewModel.stopSyncTest()
            } else {
                if (state.connectedDevices.isEmpty()) {
                    Snackbar.make(binding.root, "Connect at least one speaker first", Snackbar.LENGTH_SHORT).show()
                } else {
                    viewModel.startSyncTest()
                }
            }
        }

        binding.btnDualAudioInfo.setOnClickListener {
            showDualAudioDialog()
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val connected = state.connectedDevices.values.toList()
                    val saved = state.savedDevices

                    // Show connected + saved devices
                    adapter.submitList(connected, saved)

                    // Empty state
                    binding.tvNoDevices.visibility =
                        if (connected.isEmpty() && saved.isEmpty()) View.VISIBLE else View.GONE

                    // Sync status
                    val sync = state.syncStatus
                    binding.cardSyncStatus.visibility =
                        if (connected.size >= 2 || sync?.isPlaying == true) View.VISIBLE else View.GONE

                    sync?.let { s ->
                        binding.tvSyncMode.text = when (s.mode) {
                            PlaybackMode.SINGLE -> "Single Output Mode"
                            PlaybackMode.DUAL_AUDIO -> "Dual Audio (Hardware)"
                            PlaybackMode.SYNCHRONIZED -> "Synchronized (Software)"
                        }
                        binding.tvSyncDetail.text = when (s.mode) {
                            PlaybackMode.DUAL_AUDIO -> "Both speakers receive audio simultaneously via hardware"
                            PlaybackMode.SYNCHRONIZED -> "Audio is mirrored to both speakers — slight latency possible"
                            PlaybackMode.SINGLE -> "Only one speaker active at a time"
                        }
                        binding.tvLatencyWarning.visibility =
                            if (s.latencyWarning) View.VISIBLE else View.GONE
                        binding.btnTestSync.text = if (s.isPlaying) "Stop Test" else "Play Sync Test Tone"
                    } ?: run {
                        binding.btnTestSync.text = "Play Sync Test Tone"
                    }

                    // Capability
                    state.audioCapability?.let { cap ->
                        binding.tvCapability.text = if (cap.supportsDualAudio) {
                            "Hardware dual audio: SUPPORTED (max ${cap.maxSimultaneousOutputs} outputs)"
                        } else {
                            "Hardware dual audio: NOT SUPPORTED — software sync mode active"
                        }
                    }
                }
            }
        }
    }

    private fun showDualAudioDialog() {
        val cap = viewModel.uiState.value.audioCapability
        val isSamsung = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val message = if (isSamsung && (cap?.supportsDualAudio == true)) {
            com.btmultiplay.app.utils.SamsungDualAudioHelper.getDualAudioInstructions()
        } else if (isSamsung) {
            "Your Samsung device may support Dual Audio after an update to Android 9 (One UI 1.0) or later.\n\n" +
            "Once updated:\n" + com.btmultiplay.app.utils.SamsungDualAudioHelper.getDualAudioInstructions()
        } else {
            "Your device does not support native dual Bluetooth audio.\n\n" +
            "BT MultiPlay uses software synchronization to mirror audio across multiple speakers. " +
            "There may be a small delay between speakers depending on Bluetooth latency.\n\n" +
            "For best results, use JBL PartyBoost-compatible speakers — they synchronize internally."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Multi-Speaker Audio")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
