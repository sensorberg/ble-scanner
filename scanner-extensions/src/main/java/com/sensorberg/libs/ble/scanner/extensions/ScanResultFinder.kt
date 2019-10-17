package com.sensorberg.libs.ble.scanner.extensions

import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.observable.Cancellation
import no.nordicsemi.android.support.v18.scanner.ScanResult

fun <T> scanResultFinder(bleScanner: BleScanner,
						 cancellation: Cancellation,
						 initialSearch: List<ScanResult>?,
						 mapper: (ScanResult) -> T?,
						 onScanFound: (T) -> Unit) {
	if (cancellation.isCancelled) {
		return
	}
	// first tries to find in the accumulated list
	val found = initialSearch?.mapFirstNotNull(mapper)
	if (found != null) {
		onScanFound.invoke(found)
	} else {
		// if not in the list, listens on the bleScanner until a match is found
		with(ScanResultFinder(bleScanner, cancellation, mapper, onScanFound)) {
			bleScanner.addCallback(callback)
			cancellation.onCancelled(onCancel)
		}
	}
}

private fun <T> List<ScanResult>?.mapFirstNotNull(mapper: (ScanResult) -> T?): T? {
	this?.forEach { scanResult ->
		mapper.invoke(scanResult)
				?.let { return it }
	}
	return null
}

private class ScanResultFinder<T>(private val bleScanner: BleScanner,
								  private val cancellation: Cancellation,
								  private val mapper: (ScanResult) -> T?,
								  private val onScanFound: (T) -> Unit) {

	val onCancel: (() -> Unit) = {
		bleScanner.removeCallback(callback)
	}

	val callback = object : ScanResultCallback {
		override fun onScanResult(scanResult: ScanResult) {
			mapper.invoke(scanResult)
					?.let {
						bleScanner.removeCallback(this)
						cancellation.removeCallback(onCancel)
						onScanFound.invoke(it)
					}
		}
	}
}