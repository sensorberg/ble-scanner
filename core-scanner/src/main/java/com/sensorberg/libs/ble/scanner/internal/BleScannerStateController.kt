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

	internal fun onStateChanged(listener: (BleScanner.State) -> Unit) {
		onStateChangedListener = listener
	}

	internal fun update(bluetoothEnabled: Boolean?,
						locationEnabled: Boolean?,
						locationPermission: Boolean?,
						scanRequest: ScanRequest?,
						bluetoothError: Int) {

		if (didNotChange(bluetoothEnabled, locationEnabled, locationPermission, bluetoothError, scanRequest)) {
			return
		}

		Timber.d(
				"$TAG update: bluetoothEnabled=$bluetoothEnabled, locationEnabled=$locationEnabled, locationPermission=$locationPermission, bluetoothError=$bluetoothError, scanRequest=$scanRequest")

		when (scanRequest) {
			ScanRequest.STOP_DELAYED -> stopScan()
			ScanRequest.STOP_NOW -> stopNowScan()
			ScanRequest.SCAN -> {
				if (bluetoothEnabled == true && locationPermission == true) {
					if (bluetoothError != NO_ERROR) {
						reStartScan()
					} else {
						startScan()
					}
				} else {
					stopNowScan()
				}
			}
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

		val didNotChange = lastBluetoothError == bluetoothError
						   && lastBluetoothEnabled == bluetoothEnabled
						   && lastLocationEnabled == locationEnabled
						   && lastLocationPermission == locationPermission
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

	private fun startScan() {
		val limit = scanLimits?.getStartDelay()
		val delay = if (limit != null && limit.startsLeft <= 0) {
			limit.increaseIn
		} else {
			0L
		}
		delayedScanner.start(delay)
	}

	private fun stopScan() {
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

	private fun stopNowScan() {
		delayedScanner.stop(0)
	}

	private fun reStartScan() {
		// re-start is called due to bluetooth being turned off and on again
		// in that case we will directly stop
		delayedScanner.stop(0)
		// and then call start with any delay that was possibly calculated
		startScan()
	}

	enum class ScanRequest {
		SCAN, STOP_DELAYED, STOP_NOW
	}

	companion object {
		private const val stopDelay = 7_669L
		const val NO_ERROR = 0
	}

}