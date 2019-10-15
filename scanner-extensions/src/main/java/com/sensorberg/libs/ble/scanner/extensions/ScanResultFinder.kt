package com.sensorberg.libs.ble.scanner.extensions

import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.observable.Cancellation
import no.nordicsemi.android.support.v18.scanner.ScanResult

class ScanResultFinder(private val bleScanner: BleScanner,
					   private val cancellation: Cancellation,
					   initialSearch: List<ScanResult>?,
					   private val predicate: (ScanResult) -> Boolean,
					   private val onScanFound: (ScanResult) -> Unit) {

	private val onCancel: (() -> Unit) = {
		bleScanner.removeCallback(callback)
	}

	private val callback = object : ScanResultCallback {
		override fun onScanResult(scanResult: ScanResult) {
			if (predicate.invoke(scanResult)) {
				bleScanner.removeCallback(this)
				cancellation.removeCallback(onCancel)
				onScanFound.invoke(scanResult)
			}
		}
	}

	init {
		if (!cancellation.isCancelled) {
			val found = initialSearch?.find(predicate)
			if (found != null) {
				onScanFound.invoke(found)
			} else {
				bleScanner.addCallback(callback)
				cancellation.onCancelled(onCancel)
			}
		}
	}
}