package com.sensorberg.libs.ble.scanner.internal

import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.TAG
import timber.log.Timber
import kotlin.math.max

internal class BleScannerStateController(
	private val delayedScanner: DelayedScanner,
	private val scanLimits: ScanLimitNougat?) {

	private var lastBluetoothEnabled: Boolean? = null
	private var lastLocationEnabled: Boolean? = null
	private var lastLocationPermission: Boolean? = null
	private var lastBluetoothError: Int = NO_ERROR
	private var lastScanRequested: ScanRequest? = null

	private var currentState: BleScanner.State = BleScanner.State.IDLE(BleScanner.Reason.NO_SCAN_REQUEST)
	private var onStateChangedListener: ((BleScanner.State) -> Unit)? = null
	private var needsReStart = false

	internal fun update(bluetoothEnabled: Boolean,
						locationEnabled: Boolean,
						locationPermission: Boolean,
						scanRequest: ScanRequest) {
		privateUpdate(bluetoothEnabled, locationEnabled, locationPermission, scanRequest, lastBluetoothError)
	}

	internal fun onStateChanged(listener: (BleScanner.State) -> Unit) {
		onStateChangedListener = listener
	}

	internal fun setBluetoothError(errorCode: Int) {
		if (errorCode == lastBluetoothError) {
			return
		}
		privateUpdate(lastBluetoothEnabled, lastLocationEnabled, lastLocationPermission, lastScanRequested, errorCode)
	}

	private fun privateUpdate(bluetoothEnabled: Boolean?,
							  locationEnabled: Boolean?,
							  locationPermission: Boolean?,
							  scanRequest: ScanRequest?,
							  bluetoothError: Int) {

		if (didNotChange(bluetoothEnabled, locationEnabled, locationPermission, bluetoothError, scanRequest)) {
			return
		}

		Timber.d(
				"$TAG update: bluetoothEnabled=$bluetoothEnabled, locationEnabled=$locationEnabled, locationPermission=$locationPermission, bluetoothError=$bluetoothError, scanRequest=$scanRequest")

		// if scanning, but bluetooth is now off, needs calling restart
		if (delayedScanner.isStarted() && bluetoothEnabled == false) {
			Timber.d("$TAG. Bluetooth turned off while scanning, needs restart next time")
			needsReStart = true
		}

		// can only do anything if bluetooth is enabled
		if (bluetoothEnabled == true) {

			// if client request to stop now scanning, this is executed immediately
			if (scanRequest == ScanRequest.STOP_NOW) {
				delayedScanner.stop(0)
			}

			// if can scan, let's scan
			else if (scanRequest == ScanRequest.SCAN && locationPermission == true) {

				// re-starting if needed
				if (needsReStart || (bluetoothError != NO_ERROR)) {
					needsReStart = false
					scanReStart()
				} else {
					scanStart()
				}
			}

			// can't scan, so stop
			else {
				scanStop()
			}
		} else {
			delayedScanner.stop(0)
		}

		// here's the actual result I'm telling the application
		val newState = when {
			bluetoothError != NO_ERROR -> BleScanner.State.ERROR(bluetoothError)
			locationPermission != true -> BleScanner.State.IDLE(BleScanner.Reason.NO_LOCATION_PERMISSION)
			bluetoothEnabled != true -> BleScanner.State.IDLE(BleScanner.Reason.BLUETOOTH_DISABLED)
			locationEnabled != true -> BleScanner.State.IDLE(BleScanner.Reason.LOCATION_DISABLED)
			scanRequest != ScanRequest.SCAN -> BleScanner.State.IDLE(BleScanner.Reason.NO_SCAN_REQUEST)
			else -> BleScanner.State.SCANNING
		}

		if (newState != currentState) {
			Timber.d("$TAG. New state $newState")
			currentState = newState
			onStateChangedListener?.invoke(newState)
		}
	}

	private fun didNotChange(bluetoothEnabled: Boolean?,
							 locationEnabled: Boolean?,
							 locationPermission: Boolean?,
							 bluetoothError: Int,
							 scanRequest: ScanRequest?): Boolean {

		val didNotChange = lastBluetoothEnabled == bluetoothEnabled
						   && lastLocationEnabled == locationEnabled
						   && lastLocationPermission == locationPermission
						   && lastBluetoothError == bluetoothError
						   && lastScanRequested == scanRequest

		if (!didNotChange) {
			lastBluetoothEnabled = bluetoothEnabled
			lastLocationEnabled = locationEnabled
			lastLocationPermission = locationPermission
			lastBluetoothError = bluetoothError
			lastScanRequested = scanRequest
		}

		return didNotChange
	}

	private fun scanStart() {
		val limit = scanLimits?.getStartDelay()
		val delay = if (limit != null && limit.startsLeft <= 0) {
			limit.increaseIn
		} else {
			0L
		}
		delayedScanner.start(delay)
	}

	private fun scanStop() {
		val limit = scanLimits?.getStartDelay()
		val delay = if (limit == null) {
			0
		} else {
			if (limit.startsLeft == 0) {
				max(limit.increaseIn, stopDelay)
			} else {
				stopDelay
			}
		}
		delayedScanner.stop(delay)
	}

	private fun scanReStart() {
		// re-start is called due to bluetooth being turned off and on again
		// in that case we will directly stop
		delayedScanner.stop(0)
		// and then call start with any delay that was possibly calculated
		scanStart()
	}

	enum class ScanRequest {
		SCAN, STOP_DELAYED, STOP_NOW
	}

	companion object {
		private const val stopDelay = 7_669L
		const val NO_ERROR = 0
	}

}