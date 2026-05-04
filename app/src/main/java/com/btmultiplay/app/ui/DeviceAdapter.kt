package com.btmultiplay.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btmultiplay.app.bluetooth.BtDeviceInfo
import com.btmultiplay.app.bluetooth.ConnectionState
import com.btmultiplay.app.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onConnect: (BtDeviceInfo) -> Unit,
    private val onDisconnect: (String) -> Unit
) : ListAdapter<BtDeviceInfo, DeviceAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemDeviceBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(device: BtDeviceInfo) {
            b.tvName.text = device.displayName
            b.tvAddress.text = device.address
            b.tvType.text = device.deviceTypeLabel
            b.tvRssi.text = if (device.rssi != 0) "${device.rssi} dBm" else "Paired"

            b.chipJbl.visibility = if (device.isJbl) View.VISIBLE else View.GONE
            b.chipPartyBoost.visibility = if (device.supportsPartyBoost) View.VISIBLE else View.GONE
            b.chipA2dp.visibility = if (!device.supportsA2dp) View.VISIBLE else View.GONE

            when (device.connectionState) {
                ConnectionState.DISCONNECTED -> {
                    b.btnAction.text = "Connect"
                    b.btnAction.isEnabled = true
                    b.progressConnecting.visibility = View.GONE
                    b.ivStatus.setImageResource(com.btmultiplay.app.R.drawable.ic_bluetooth_disabled)
                }
                ConnectionState.CONNECTING -> {
                    b.btnAction.text = "Connecting…"
                    b.btnAction.isEnabled = false
                    b.progressConnecting.visibility = View.VISIBLE
                    b.ivStatus.setImageResource(com.btmultiplay.app.R.drawable.ic_bluetooth)
                }
                ConnectionState.CONNECTED -> {
                    b.btnAction.text = "Disconnect"
                    b.btnAction.isEnabled = true
                    b.progressConnecting.visibility = View.GONE
                    b.ivStatus.setImageResource(com.btmultiplay.app.R.drawable.ic_bluetooth_connected)
                }
                ConnectionState.DISCONNECTING -> {
                    b.btnAction.text = "Disconnecting…"
                    b.btnAction.isEnabled = false
                    b.progressConnecting.visibility = View.VISIBLE
                    b.ivStatus.setImageResource(com.btmultiplay.app.R.drawable.ic_bluetooth)
                }
            }

            b.btnAction.setOnClickListener {
                if (device.connectionState == ConnectionState.CONNECTED ||
                    device.connectionState == ConnectionState.CONNECTING) {
                    onDisconnect(device.address)
                } else {
                    onConnect(device)
                }
            }

            // Signal strength bar
            b.progressSignal.progress = when {
                device.rssi == 0 -> 80
                device.rssi >= -60 -> 100
                device.rssi >= -70 -> 75
                device.rssi >= -80 -> 50
                device.rssi >= -90 -> 25
                else -> 10
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BtDeviceInfo>() {
            override fun areItemsTheSame(a: BtDeviceInfo, b: BtDeviceInfo) = a.address == b.address
            override fun areContentsTheSame(a: BtDeviceInfo, b: BtDeviceInfo) = a == b
        }
    }
}
