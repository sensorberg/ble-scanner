package com.sensorberg.libs.ble_scanner.sample.recorder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.libs.ble_scanner.sample.bleScanner
import no.nordicsemi.android.support.v18.scanner.ScanResult

class RecorderViewModel(application: Application) : AndroidViewModel(application) {

	private var isRecording = false

	fun record(title: String, address: String) {
		if (isRecording) {
			return
		}
		isRecording = true
		bleScanner.addCallback(scanResultCallback)
	}

	override fun onCleared() {
		bleScanner.removeCallback(scanResultCallback)
	}

	private val scanResultCallback = object : ScanResultCallback {
		override fun onScanResult(scanResult: ScanResult) {

		}
	}
}