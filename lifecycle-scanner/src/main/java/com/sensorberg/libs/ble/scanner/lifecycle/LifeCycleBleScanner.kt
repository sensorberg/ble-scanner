package com.sensorberg.libs.ble.scanner.lifecycle

import androidx.lifecycle.*
import com.sensorberg.libs.ble.scanner.BleScanner

/**
 * This methods simply calls [BleScanner.start] and [BleScanner.stopDelayed] according to the supplied lifecycle start/stop events.
 */
fun BleScanner.scanWithLifecycle(lifecycleOwner: LifecycleOwner) {
	val lifecycleObserver = StartedObserver(this, lifecycleOwner)
	lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
}

fun BleScanner.scanWithProcessLifecycle() {
	scanWithLifecycle(ProcessLifecycleOwner.get())
}

internal class StartedObserver(private val scanner: BleScanner, private val lifecycleOwner: LifecycleOwner) : LifecycleObserver {

	@OnLifecycleEvent(Lifecycle.Event.ON_START) fun onStart() {
		scanner.start()
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_STOP) fun onStop() {
		scanner.stopDelayed()
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY) fun onDestroy() {
		scanner.stopNow()
		lifecycleOwner.lifecycle.removeObserver(this)
	}
}