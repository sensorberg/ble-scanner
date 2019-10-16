package com.sensorberg.libs.ble.scanner.extensions

import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.observable.Cancellation
import no.nordicsemi.android.support.v18.scanner.ScanResult

class ScanResultFinder<T>(private val bleScanner: BleScanner,
						  private val cancellation: Cancellation,
						  initialSearch: List<ScanResult>?,
						  private val mapper: (ScanResult) -> T?,
						  private val onScanFound: (T) -> Unit) {

	private val onCancel: (() -> Unit) = {
		bleScanner.removeCallback(callback)
	}

	private val callback = object : ScanResultCallback {
		override fun onScanResult(scanResult: ScanResult) {
			mapper.invoke(scanResult)
					?.let {
						bleScanner.removeCallback(this)
						cancellation.removeCallback(onCancel)
						onScanFound.invoke(it)
					}
		}
	}

	init {
		if (!cancellation.isCancelled) {
			val found = initialSearch?.mapFirstNotNull(mapper)
			if (found != null) {
				onScanFound.invoke(found)
			} else {
				bleScanner.addCallback(callback)
				cancellation.onCancelled(onCancel)
			}
		}
	}

	private fun List<ScanResult>?.mapFirstNotNull(mapper: (ScanResult) -> T?): T? {
		this?.forEach { scanResult ->
			mapper.invoke(scanResult)
					?.let { return it }
		}
		return null
	}
}