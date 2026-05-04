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
import com.btmultiplay.app.BuildConfig
import com.btmultiplay.app.databinding.FragmentSettingsBinding
import com.btmultiplay.app.utils.SamsungDualAudioHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Device info
        binding.tvDeviceModel.text = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        binding.tvAndroidVersion.text = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        binding.tvAppVersion.text = "App Version: ${BuildConfig.VERSION_NAME}"

        // Samsung info
        val isSamsung = SamsungDualAudioHelper.isSamsungDevice
        binding.tvSamsungStatus.text = if (isSamsung) {
            SamsungDualAudioHelper.getCapabilityDescription(requireContext())
        } else {
            "Not a Samsung device — standard single BT output"
        }

        binding.btnCheckUpdates.setOnClickListener {
            val updater = viewModel.capabilityUpdater
            if (!updater.isNetworkAvailable()) {
                Snackbar.make(binding.root, "No internet connection", Snackbar.LENGTH_SHORT).show()
            } else {
                viewModel.checkForUpdates()
                Snackbar.make(binding.root, "Checking for updates…", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnDualAudioHelp.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Enable Samsung Dual Audio")
                .setMessage(SamsungDualAudioHelper.getDualAudioInstructions())
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnClearSavedDevices.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear All Saved Devices")
                .setMessage("Remove all saved speakers from this device? Currently connected speakers won't be disconnected.")
                .setPositiveButton("Clear") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.repository.deleteAll()
                        Snackbar.make(binding.root, "Saved devices cleared", Snackbar.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnDualAudioHelp.visibility = if (isSamsung) View.VISIBLE else View.GONE

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.audioCapability?.let { cap ->
                        binding.tvAudioCapability.text = buildString {
                            appendLine("Dual Audio: ${if (cap.supportsDualAudio) "Supported" else "Not supported"}")
                            appendLine("Max simultaneous outputs: ${cap.maxSimultaneousOutputs}")
                            append("Bluetooth SCO: ${if (cap.supportsBluetoothSco) "Available" else "Not available"}")
                        }
                    }

                    binding.tvUpdateStatus.text = when {
                        state.isCheckingUpdates -> "Checking for updates…"
                        state.updateResult?.success == true -> "Last check: ${state.updateResult.message}"
                        state.updateResult?.success == false -> "Update failed: ${state.updateResult.message}"
                        else -> "Tap 'Check Updates' to fetch latest compatibility data"
                    }

                    binding.tvSavedCount.text = "Saved speakers: ${state.savedDevices.size}"
                    binding.tvConnectedCount.text = "Currently connected: ${state.connectedDevices.size}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
