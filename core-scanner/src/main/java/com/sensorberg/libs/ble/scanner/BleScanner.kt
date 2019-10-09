package com.sensorberg.libs.ble.scanner

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.sensorberg.libs.ble.scanner.internal.BleScannerImpl
import com.sensorberg.libs.ble.scanner.internal.BleScannerStateController
import com.sensorberg.libs.ble.scanner.internal.DelayedScannerImpl
import com.sensorberg.libs.ble.scanner.internal.ScanLimitNougat
import com.sensorberg.libs.ble.scanner.internal.dependency.BluetoothEnabled
import com.sensorberg.libs.ble.scanner.internal.dependency.LocationEnabled
import com.sensorberg.libs.ble.scanner.internal.dependency.LocationPermission
import com.sensorberg.observable.ObservableData
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings

internal const val TAG = "BleScanner"

interface BleScanner {

	fun start()
	fun stopDelayed()
	fun stopNow()

	fun addCallback(callback: ScanResultCallback)
	fun removeCallback(callback: ScanResultCallback)
	fun getState(): ObservableData<State>

	companion object {
		private val isNougat: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

		fun builder(context: Context): Builder {
			return Builder(context)
		}
	}

	sealed class State {
		data class IDLE(val why: Reason) : State() {
			override fun toString(): String {
				return "IDLE - $why"
			}
		}

		object SCANNING : State() {
			override fun toString(): String {
				return "SCANNING"
			}
		}

		data class ERROR(val errorCode: Int) : State() {
			override fun toString(): String {
				return "ERROR - $errorCode"
			}
		}
	}

	enum class Reason {
		NO_SCAN_REQUEST,
		BLUETOOTH_DISABLED,
		LOCATION_DISABLED,
		NO_LOCATION_PERMISSION
	}

	class Builder internal constructor(context: Context) {

		private val appContext = context.applicationContext

		private var settings = ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				.setUseHardwareFilteringIfSupported(false)
				.build()

		private var filters: List<ScanFilter>? = null

		private var handler: Handler? = null

		private fun lazyHandler(): Lazy<Handler> {
			val h: Handler? = handler
			return if (h != null) {
				val nonNull: Handler = h
				lazy { nonNull }
			} else {
				val ht = HandlerThread("ble-scanner", Thread.MIN_PRIORITY).apply { start() }
				lazy { Handler(ht.looper) }
			}
		}

		fun settings(settings: ScanSettings): Builder {
			this.settings = settings
			return this
		}

		fun filters(filters: List<ScanFilter>): Builder {
			this.filters = filters
			return this
		}

		fun handler(handler: Handler): Builder {
			this.handler = handler
			return this
		}

		fun build(): BleScanner {

			val scanLimitNougat: ScanLimitNougat? = if (isNougat) {
				ScanLimitNougat()
			} else {
				null
			}

			val delayedScanner = DelayedScannerImpl(filters,
													settings,
													BluetoothLeScannerCompat.getScanner(),
													lazyHandler(),
													scanLimitNougat,
													BluetoothEnabled.isBluetoothOn)

			val controller = BleScannerStateController(delayedScanner,
													   scanLimitNougat)

			return BleScannerImpl(controller,
								  delayedScanner,
								  BluetoothEnabled(appContext),
								  LocationEnabled(appContext),
								  LocationPermission(appContext))
		}
	}
}