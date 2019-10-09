package com.sensorberg.libs.ble.scanner.internal

import android.os.Handler
import com.sensorberg.libs.ble.scanner.TAG
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber

internal class DelayedScannerImpl(private val filters: List<ScanFilter>?,
								  private val settings: ScanSettings,
								  private val scanner: BluetoothLeScannerCompat,
								  lazyHandler: Lazy<Handler>,
								  private val scanLimits: ScanLimitNougat?,
								  private val checkBluetoothEnabled: () -> Boolean) : DelayedScanner {

	private val handler by lazyHandler
	private val startStop = StartStop()
	private lateinit var scanCallback: ScanCallback

	override fun setScanCallback(callback: ScanCallback) {
		this.scanCallback = callback
	}

	override fun isStarted(): Boolean {
		return startStop.isStarted()
	}

	override fun start(delay: Long) {
		removeCallbacks()
		if (delay == 0L) {
			startRunnable.run()
		} else {
			Timber.d("$TAG. Scheduling start to scan in ${delay}ms")
			handler.postDelayed(startRunnable, delay)
		}
	}

	override fun stop(delay: Long) {
		removeCallbacks()
		if (delay == 0L) {
			stopRunnable.run()
		} else {
			Timber.d("$TAG. Scheduling scanner stop in ${delay}ms")
			handler.postDelayed(stopRunnable, delay)
		}
	}

	private fun removeCallbacks() {
		handler.removeCallbacks(startRunnable)
		handler.removeCallbacks(stopRunnable)
	}

	private val startRunnable = Runnable {
		if (!checkBluetoothEnabled()) return@Runnable
		startStop.start {
			Timber.d("$TAG. Starting scanner for real")
			scanner.startScan(filters, settings, scanCallback, handler)
			scanLimits?.onAfterStart()
		}
	}
	private val stopRunnable = Runnable {
		startStop.stop {
			Timber.d("$TAG. Stopping scanner for real")
			scanner.stopScan(scanCallback)
		}
	}
}