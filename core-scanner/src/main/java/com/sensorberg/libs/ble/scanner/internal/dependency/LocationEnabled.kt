package com.sensorberg.libs.ble.scanner.internal.dependency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import com.sensorberg.libs.ble.scanner.TAG
import com.sensorberg.libs.ble.scanner.internal.StartStop
import com.sensorberg.observable.ObservableData
import timber.log.Timber

class LocationEnabled(private val context: Context) : ObservableData<Boolean>() {

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
		val enabled = isLocationOn(context)
		if (value == enabled) {
			return
		}
		if (enabled) {
			Timber.d("$TAG. Location is enabled")
		} else {
			Timber.d("$TAG. Location is disable")
		}
		value = enabled
	}

	fun start() {
		startStop.start {
			value = isLocationOn(context)
			context.registerReceiver(receiver, FILTER)
		}
	}

	fun stop() {
		startStop.stop {
			context.unregisterReceiver(receiver)
		}
	}

	companion object {
		private val FILTER = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)

		fun isLocationOn(context: Context): Boolean {
			val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
			return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
				   lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
				   lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
		}

	}

}