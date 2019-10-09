package com.sensorberg.libs.ble.scanner.internal.dependency

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.sensorberg.libs.ble.scanner.TAG
import com.sensorberg.libs.ble.scanner.internal.StartStop
import com.sensorberg.observable.ObservableData
import timber.log.Timber

internal class BluetoothEnabled(private val context: Context) : ObservableData<Boolean>() {

	private val startStop = StartStop()

	init {
		updateState()
	}

	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			updateState()
		}
	}

	private fun updateState() {
		val enabled = isBluetoothOn()
		if (value == enabled) {
			return
		}
		if (enabled) {
			Timber.d("$TAG. Bluetooth is enabled")
		} else {
			Timber.d("$TAG. Bluetooth is disable")
		}
		value = enabled
	}

	fun start() {
		startStop.start {
			this.value = isBluetoothOn()
			context.registerReceiver(receiver, FILTER)
		}
	}

	fun stop() {
		startStop.stop {
			context.unregisterReceiver(receiver)
		}
	}

	companion object {
		private val FILTER = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)

		val isBluetoothOn: () -> Boolean = ::isBluetoothOn

		private fun isBluetoothOn(): Boolean {
			val adapter = BluetoothAdapter.getDefaultAdapter()
			return (adapter?.isEnabled == true)
				   && (adapter?.bluetoothLeScanner != null)
		}
	}
}