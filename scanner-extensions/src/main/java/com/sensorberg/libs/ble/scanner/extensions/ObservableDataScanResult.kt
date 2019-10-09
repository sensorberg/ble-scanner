package com.sensorberg.libs.ble.scanner.extensions

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.libs.time.ElapsedTime
import com.sensorberg.libs.time.Time
import com.sensorberg.motionlessaverage.MotionlessAverage
import com.sensorberg.observable.Cancellation
import com.sensorberg.observable.ObservableData
import com.sensorberg.observable.Observer
import no.nordicsemi.android.support.v18.scanner.ScanResult
import timber.log.Timber

internal const val TAG = "BleScanner.Observable"

class ObservableDataScanResult private constructor(private val timeoutInMs: Long,
												   private val onActiveTimeoutInMs: Long,
												   private val debounceInMs: Long,
												   private val averagerFactory: AveragerFactory,
												   lazyHandler: Lazy<Handler>) : ObservableData<List<BleScanResult>>() {

	private val removeOldDelayInMs = debounceInMs + 10L
	private val debounce = object {}
	private var lastPost: ElapsedTime? = null
	private val devices = mutableMapOf<String, BleScanResultContainer>()
	private val handler by lazyHandler
	private val removeOldRunnable = Runnable {
		if (dropDevices(timeoutInMs)) {
			postDevices()
		}
	}

	override fun onActive() {
		if (dropDevices(onActiveTimeoutInMs)) {
			postDevices()
		}
	}

	override fun onInactive() {
		handler.removeCallbacks(removeOldRunnable)
		lastPost = null
	}

	private fun postDevices() {
		if (!hasObservers()) {
			return
		}

		synchronized(debounce) {
			lastPost?.let {
				if (it.elapsed() <= debounceInMs) {
					return
				}
			}
			lastPost = Time.elapsed()
		}

		val unsorted = synchronized(devices) {
			devices.values.map { it.bleScanResult }
		}
		val sorted = unsorted.sortedBy { -it.averageRssi }
		Timber.d("$TAG. Posting ${sorted.size} scan results")
		value = sorted
	}

	private fun dropDevices(timeoutInMs: Long): Boolean {
		val now = now()
		synchronized(devices) {
			return devices.filterInPlace { isTimedOut(it.bleScanResult.scanResult, now, timeoutInMs) } > 0
		}
	}

	private val onBleScannerStatus: Observer<BleScanner.State> = {

	}

	private val onScanResults = object : ScanResultCallback {
		override fun onScanResult(scanResult: ScanResult) {
			handler.removeCallbacks(removeOldRunnable)
			scanResult.timestampNanos
			SystemClock.elapsedRealtime()
			val floatRssi = scanResult.rssi.toFloat()
			val key = key(scanResult)
			synchronized(devices) {
				val averager = devices[key]?.averager ?: averagerFactory.invoke()
				val averageRssi = averager?.average(floatRssi)
				val resultExtras = BleScanResult(scanResult, averageRssi ?: floatRssi)
				val internalData = BleScanResultContainer(resultExtras, averager)
				devices[key] = internalData
			}
			dropDevices(timeoutInMs)
			postDevices()
			handler.postDelayed(removeOldRunnable, removeOldDelayInMs)
		}
	}

	companion object {

		private inline fun <T> MutableMap<String, T>.filterInPlace(filter: (T) -> Boolean): Int {
			var removed = 0
			val iterate = iterator()
			while (iterate.hasNext()) {
				if (filter(iterate.next().value)) {
					removed++
					iterate.remove()
				}
			}
			return removed
		}

		private fun key(scanResult: ScanResult): String {
			return scanResult.device.address
		}

		private fun now(): Long {
			return SystemClock.elapsedRealtime()
		}

		private fun nanoToMillis(nanos: Long): Long {
			return nanos / 1_000_000L
		}

		private fun isTimedOut(scan: ScanResult, now: Long, timeout: Long): Boolean {
			return nanoToMillis(scan.timestampNanos) + timeout < now
		}

		fun builder(bleScanner: BleScanner): Builder {
			return Builder(bleScanner)
		}
	}

	class Builder internal constructor(private val bleScanner: BleScanner) {

		private var onActiveTimeoutInMs = 14000L
		private var timeoutInMs = 7000L
		private var debounceInMs = 1173L
		private var cancellation: Cancellation? = null
		private var averagerFactory: AveragerFactory = {
			MotionlessAverage.Builder.createConstantFilterAverage(3f)
		}

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

		fun timeoutInMs(timeout: Long): Builder {
			timeoutInMs = timeout
			return this
		}

		fun onActiveTimeoutInMs(onActiveTimeoutInMs: Long): Builder {
			this.onActiveTimeoutInMs = onActiveTimeoutInMs
			return this
		}

		fun debounceInMs(debounce: Long): Builder {
			debounceInMs = debounce
			return this
		}

		fun cancellation(cancellation: Cancellation): Builder {
			this.cancellation = cancellation
			return this
		}

		fun handler(handler: Handler): Builder {
			this.handler = handler
			return this
		}

		fun build(): ObservableData<List<BleScanResult>> {
			val result = ObservableDataScanResult(timeoutInMs, onActiveTimeoutInMs, debounceInMs, averagerFactory, lazyHandler())
			bleScanner.addCallback(result.onScanResults)
			val state = bleScanner.getState()
			state.observe(result.onBleScannerStatus)
			cancellation?.onCancelled {
				bleScanner.removeCallback(result.onScanResults)
				state.removeObserver(result.onBleScannerStatus)
			}
			return result
		}

	}

	/**
	 * This is used just to keep together the scanResult and the averager
	 */
	private class BleScanResultContainer(val bleScanResult: BleScanResult,
										 val averager: MotionlessAverage?)
}

typealias AveragerFactory = () -> MotionlessAverage?