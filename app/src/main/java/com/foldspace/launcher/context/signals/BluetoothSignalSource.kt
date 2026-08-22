package com.foldspace.launcher.context.signals

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothClass as AndroidBluetoothClass
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.foldspace.launcher.context.BluetoothClass
import com.foldspace.launcher.context.ContextEvent

/**
 * §7 Bluetooth signal. Connection-state changes only — never a scan, never a
 * poll. §16.1: only the coarse device *class* is retained, never the device
 * name or address.
 */
class BluetoothSignalSource(
    private val context: Context,
    private val onEvent: (ContextEvent) -> Unit,
) {

    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        if (!hasPermission()) return

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
                if (!connected) {
                    onEvent(ContextEvent.BluetoothChanged(BluetoothClass.None))
                    return
                }
                val device = intent.deviceExtra() ?: return
                onEvent(ContextEvent.BluetoothChanged(classify(device)))
            }
        }
        receiver = r
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun Intent.deviceExtra(): BluetoothDevice? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    /** Maps the platform's major device class onto our coarse buckets. */
    private fun classify(device: BluetoothDevice): BluetoothClass {
        val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
            ?: return BluetoothClass.Other

        return when (major) {
            AndroidBluetoothClass.Device.Major.AUDIO_VIDEO -> {
                val minor = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
                if (minor == AndroidBluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    minor == AndroidBluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
                ) {
                    BluetoothClass.Car
                } else {
                    BluetoothClass.Audio
                }
            }

            AndroidBluetoothClass.Device.Major.WEARABLE -> BluetoothClass.Wearable
            else -> BluetoothClass.Other
        }
    }

    private fun hasPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}
