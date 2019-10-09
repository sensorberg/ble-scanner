package com.sensorberg.libs.ble.scanner.internal

import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.libs.ble.scanner.TAG
import com.sensorberg.libs.ble.scanner.internal.dependency.BluetoothEnabled
import com.sensorberg.libs.ble.scanner.internal.dependency.LocationEnabled
import com.sensorberg.libs.ble.scanner.internal.dependency.LocationPermission
import com.sensorberg.observable.MutableObservableData
import com.sensorberg.observable.ObservableData
import com.sensorberg.observable.Observer
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import timber.log.Timber

internal class BleScannerImpl(private val controller: BleScannerStateController,
							  delayedScanner: DelayedScanner,
							  private val bluetoothEnabled: BluetoothEnabled,
							  private val locationEnabled: LocationEnabled,
							  private val locationPermission: LocationPermission) : BleScanner {

	private val callbacks = mutableListOf<ScanResultCallback>()
	private val startStop = MutableObservableData<BleScannerStateController.ScanRequest>()
	private val state = MutableObservableData<BleScanner.State>()

	private val updater: Observer<Any> = {
		controller.update(bluetoothEnabled.value ?: false,
						  locationEnabled.value ?: false,
						  locationPermission.value ?: false,
						  startStop.value ?: BleScannerStateController.ScanRequest.STOP_NOW)
	}

	init {
		delayedScanner.setScanCallback(initScanCallback())
		controller.onStateChanged { state.value = it }
		bluetoothEnabled.observe(updater)
		locationEnabled.observe(updater)
		locationPermission.observe(updater)
		startStop.observe(updater)
		bluetoothEnabled.start()
		locationEnabled.start()
	}

	override fun start() {
		Timber.d("$TAG. Requesting to start scanner")
		startStop.value = BleScannerStateController.ScanRequest.SCAN
	}

	override fun stopDelayed() {
		Timber.d("$TAG. Requesting to stop scanner delayed")
		startStop.value = BleScannerStateController.ScanRequest.STOP_DELAYED
	}

	override fun stopNow() {
		Timber.d("$TAG. Requesting to stop scanner now")
		startStop.value = BleScannerStateController.ScanRequest.STOP_NOW
	}

	override fun getState(): ObservableData<BleScanner.State> {
		return state
	}

	override fun addCallback(callback: ScanResultCallback) {
		synchronized(callbacks) {
			if (callbacks.contains(callback)) return
			callbacks.add(callback)
		}
	}

	override fun removeCallback(callback: ScanResultCallback) {
		synchronized(callbacks) {
			callbacks.remove(callback)
		}
	}

	private fun initScanCallback(): ScanCallback {
		return object : ScanCallback() {
			override fun onScanResult(callbackType: Int, result: ScanResult) {

				// avoid weird / bad scan results
				if (!isGoodScan(result)) {
					return
				}

				// if we're receiving scans, remove any error
				controller.setBluetoothError(BleScannerStateController.NO_ERROR)

				// only report if the application believes we're scanning
				if (state.value == BleScanner.State.SCANNING) {
					synchronized(callbacks) { callbacks.forEach { it.onScanResult(result) } }
				}
			}

			override fun onScanFailed(errorCode: Int) {
				controller.setBluetoothError(errorCode)
			}
		}
	}

	companion object {
		private fun isGoodScan(scanResult: ScanResult?): Boolean {

			// WTF Android?
			if (scanResult?.device == null) {
				return false
			}

			// WTF Samsung?
			if (scanResult.rssi >= 0) {
				return false
			}

			// corrupted scan
			if (scanResult.scanRecord == null) {
				return false
			}

			return true
		}
	}

}