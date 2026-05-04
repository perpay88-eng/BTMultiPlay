package com.btmultiplay.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.btmultiplay.app.bluetooth.BtDeviceInfo
import com.btmultiplay.app.data.SavedDevice
import com.btmultiplay.app.databinding.ItemConnectedDeviceBinding

sealed class DeviceListItem {
    data class Connected(val device: BtDeviceInfo) : DeviceListItem()
    data class Saved(val device: SavedDevice, val isOnline: Boolean = false) : DeviceListItem()
}

class ConnectedDeviceAdapter(
    private val onDisconnect: (String) -> Unit,
    private val onForget: (String, String) -> Unit,
    private val onSetAutoReconnect: (String, Boolean) -> Unit
) : RecyclerView.Adapter<ConnectedDeviceAdapter.ViewHolder>() {

    private val items = mutableListOf<DeviceListItem>()

    fun submitList(
        connected: List<BtDeviceInfo>,
        saved: List<SavedDevice>
    ) {
        val connectedAddrs = connected.map { it.address }.toSet()
        val newItems = mutableListOf<DeviceListItem>()
        connected.forEach { newItems.add(DeviceListItem.Connected(it)) }
        saved.filter { it.address !in connectedAddrs }
            .forEach { newItems.add(DeviceListItem.Saved(it)) }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val old = items[o]; val new = newItems[n]
                return when {
                    old is DeviceListItem.Connected && new is DeviceListItem.Connected -> old.device.address == new.device.address
                    old is DeviceListItem.Saved && new is DeviceListItem.Saved -> old.device.address == new.device.address
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    inner class ViewHolder(private val b: ItemConnectedDeviceBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bindConnected(item: DeviceListItem.Connected) {
            val d = item.device
            b.tvName.text = d.displayName
            b.tvAddress.text = d.address
            b.tvStatus.text = "Connected"
            b.tvStatus.setTextColor(b.root.context.getColor(com.btmultiplay.app.R.color.status_connected))
            b.chipJbl.visibility = if (d.isJbl) View.VISIBLE else View.GONE
            b.chipPartyBoost.visibility = if (d.supportsPartyBoost) View.VISIBLE else View.GONE
            b.ivConnected.setImageResource(com.btmultiplay.app.R.drawable.ic_bluetooth_connected)
            b.switchAutoReconnect.visibility = View.VISIBLE
            b.btnDisconnect.text = "Disconnect"
            b.btnDisconnect.setOnClickListener { onDisconnect(d.address) }
            b.btnForget.setOnClickListener { onForget(d.address, d.displayName) }
            b.switchAutoReconnect.setOnCheckedChangeListener(null)
            b.switchAutoReconnect.isChecked = true
            b.switchAutoReconnect.setOnCheckedChangeListener { _, checked ->
                onSetAutoReconnect(d.address, checked)
            }
        }

        fun bindSaved(item: DeviceListItem.Saved) {
            val d = item.device
            b.tvName.text = d.name.ifBlank { d.address }
            b.tvAddress.text = d.address
            b.tvStatus.text = "Saved — Not Connected"
            b.tvStatus.setTextColor(b.root.context.getColor(com.btmultiplay.app.R.color.status_disconnected))
            b.chipJbl.visibility = if (d.isJbl) View.VISIBLE else View.GONE
            b.chipPartyBoost.visibility = if (d.supportsPartyBoost) View.VISIBLE else View.GONE
            b.ivConnected.setImageResource(com.btmultiplay.app.R.drawable.ic_bluetooth_disabled)
            b.switchAutoReconnect.visibility = View.VISIBLE
            b.btnDisconnect.text = "Reconnect"
            b.btnDisconnect.setOnClickListener { /* trigger reconnect via viewmodel if needed */ }
            b.btnForget.setOnClickListener { onForget(d.address, d.name) }
            b.switchAutoReconnect.setOnCheckedChangeListener(null)
            b.switchAutoReconnect.isChecked = d.autoReconnect
            b.switchAutoReconnect.setOnCheckedChangeListener { _, checked ->
                onSetAutoReconnect(d.address, checked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemConnectedDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DeviceListItem.Connected -> holder.bindConnected(item)
            is DeviceListItem.Saved -> holder.bindSaved(item)
        }
    }

    override fun getItemCount() = items.size
}
