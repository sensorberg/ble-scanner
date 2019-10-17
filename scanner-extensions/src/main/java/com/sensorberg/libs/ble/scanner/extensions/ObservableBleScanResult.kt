package com.sensorberg.libs.ble.scanner.extensions

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.libs.ble.scanner.extensions.ObservableBleScanResult.Builder
import com.sensorberg.libs.time.ElapsedTime
import com.sensorberg.libs.time.Time
import com.sensorberg.motionlessaverage.MotionlessAverage
import com.sensorberg.observable.Cancellation
import com.sensorberg.observable.ObservableData
import com.sensorberg.observable.Observer
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import timber.log.Timber

internal const val TAG = "BleScanner.Observable"

/**
 * An [ObservableData] that supplies a list of ScanResult and average RSSI.
 *
 * To get an instance of it use the [Builder]
 *
 *      ObservableBleScanResult
 *          // scanner to supply ScanResult data
 *          .builder(bleScanner)
 *          // after how long without seeing a device, it should be removed from the list
 *          .timeoutInMs(FIVE_SECONDS)
 *          // when become active, drop any device not seen for this amount of time
 *          .onActiveTimeoutInMs(TEN_SECONDS)
 *          // avoid sending new data too frequently
 *          .debounceInMs(ONE_SECONDS)
 *          // cancels connection to the bleScanner
 *          .cancellation(cancellation)
 *          // filters only specific devices
 *          .filters(listOf(ScanFilter.Builder().setDeviceName("my_device").build()))
 *          // handler to execute delayed the removal of timed-out devices. (ideally the same used to instantiate the BleScanner)
 *          .handler(handler)
 *          // factory for an averaging algorithm
 *          .averagerFactory { MotionlessAverage.Builder.createConstantFilterAverage(3f) }
 *          .build()
 *
 */
class ObservableBleScanResult private constructor(private val timeoutInMs: Long,
												  private val onActiveTimeoutInMs: Long,
												  private val debounceInMs: Long,
												  private val averagerFactory: AveragerFactory?,
												  private val filters: List<ScanFilter>?,
												  lazyHandler: Lazy<Handler>) : ObservableData<List<BleScanResult>>() {

	private val removeOldDelayInMs = debounceInMs + 10L
	private val debounce = object {}
	private var lastPost: ElapsedTime? = null
	private val devices = mutableMapOf<String, BleScanResultContainer>()
	private val handler by lazyHandler
	private val removeOldRunnable = Runnable {
		if (dropDevices(timeoutInMs)) {
			if (hasObservers()) {
				postDevices()
			} else {
				postDevicesNow()
			}
		}
		removeOldDelayed()
	}

	override fun onActive() {
		if (dropDevices(onActiveTimeoutInMs)) {
			postDevicesNow()
		}
	}

	override fun onInactive() {
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
		}

		postDevicesNow()
	}

	private fun size(): Int = synchronized(devices) { devices.size }

	private fun postDevicesNow() {

		synchronized(debounce) {
			lastPost = Time.elapsed()
		}

		val unsorted = synchronized(devices) {
			devices.values.map { it.bleScanResult }
		}

		val sorted = unsorted.sortedBy { -it.averageRssi }
		Timber.d("$TAG. Posting ${sorted.size} scan results")
		value = sorted
	}

	private fun removeOldDelayed() {
		handler.removeCallbacks(removeOldRunnable)
		if (hasObservers() && size() > 0) {
			handler.postDelayed(removeOldRunnable, removeOldDelayInMs)
		}
	}

	private fun dropDevices(timeoutInMs: Long): Boolean {
		val now = now()
		synchronized(devices) {
			return devices.filterInPlace { isTimedOut(it.bleScanResult.scanResult, now, timeoutInMs) } > 0
		}
	}

	private fun clearAllDevices() {
		synchronized(devices) {
			devices.clear()
		}
		postDevicesNow()
	}

	private val onBleScannerStatus: Observer<BleScanner.State> = {
		when (it) {
			is BleScanner.State.IDLE -> {
				when (it.why) {
					BleScanner.Reason.BLUETOOTH_DISABLED,
					BleScanner.Reason.LOCATION_DISABLED,
					BleScanner.Reason.NO_LOCATION_PERMISSION -> clearAllDevices()
				}
			}
			is BleScanner.State.ERROR -> {
				clearAllDevices()
			}
		}
	}

	private val onScanResults = object : ScanResultCallback {
		override fun onScanResult(scanResult: ScanResult) {

			if (filters?.none { it.matches(scanResult) } == true) {
				return
			}

			handler.removeCallbacks(removeOldRunnable)
			scanResult.timestampNanos
			SystemClock.elapsedRealtime()
			val floatRssi = scanResult.rssi.toFloat()
			val key = key(scanResult)
			synchronized(devices) {
				val averager = devices[key]?.averager ?: averagerFactory?.invoke()
				val averageRssi = averager?.average(floatRssi)
				val resultExtras = BleScanResult(scanResult, averageRssi ?: floatRssi)
				val internalData = BleScanResultContainer(resultExtras, averager)
				devices[key] = internalData
			}
			dropDevices(timeoutInMs)
			postDevices()
			removeOldDelayed()
		}
	}

	/**
	 * An [ObservableData] that supplies a list of ScanResult and average RSSI.
	 *
	 * To get an instance of it use the [Builder]
	 *
	 *      ObservableBleScanResult
	 *          // scanner to supply ScanResult data
	 *          .builder(bleScanner)
	 *          // after how long without seeing a device, it should be removed from the list
	 *          .timeoutInMs(FIVE_SECONDS)
	 *          // when become active, drop any device not seen for this amount of time
	 *          .onActiveTimeoutInMs(TEN_SECONDS)
	 *          // avoid sending new data too frequently
	 *          .debounceInMs(ONE_SECONDS)
	 *          // cancels connection to the bleScanner
	 *          .cancellation(cancellation)
	 *          // filters only specific devices
	 *          .filters(listOf(ScanFilter.Builder().setDeviceName("my_device").build()))
	 *          // handler to execute delayed the removal of timed-out devices. (ideally the same used to instantiate the BleScanner)
	 *          .handler(handler)
	 *          // factory for an averaging algorithm
	 *          .averagerFactory { MotionlessAverage.Builder.createConstantFilterAverage(3f) }
	 *          .build()
	 *
	 */
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
			return Time.getElapsedTime()
		}

		private fun nanoToMillis(nanos: Long): Long {
			return nanos / 1_000_000L
		}

		private fun isTimedOut(scan: ScanResult, now: Long, timeout: Long): Boolean {
			return nanoToMillis(scan.timestampNanos) + timeout < now
		}

		/**
		 * Creates a [ObservableBleScanResult.Builder] to observe [ScanResult] on the supplied [BleScanner].
		 */
		fun builder(bleScanner: BleScanner): Builder {
			return Builder(bleScanner)
		}
	}

	/**
	 * Builder for instances of the [ObservableBleScanResult]
	 */
	class Builder internal constructor(private val bleScanner: BleScanner) {

		private var filters: List<ScanFilter>? = null
		private var onActiveTimeoutInMs = 14000L
		private var timeoutInMs = 7000L
		private var debounceInMs = 1173L
		private var cancellation: Cancellation? = null
		private var averagerFactory: AveragerFactory? = null

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

		/**
		 * A timeout (in milliseconds) after how long without seeing a device, it should be removed from the list
		 */
		fun timeoutInMs(timeout: Long): Builder {
			timeoutInMs = timeout
			return this
		}

		/**
		 * When the observable become active, drop any device not seen for this amount of time (in milliseconds).
		 * That is an optimization because to avoid calling removeOld() while no-one is watching
		 */
		fun onActiveTimeoutInMs(onActiveTimeoutInMs: Long): Builder {
			this.onActiveTimeoutInMs = onActiveTimeoutInMs
			return this
		}

		/**
		 * A debounce time (in milliseconds) to avoid sending new data too frequently
		 */
		fun debounceInMs(debounce: Long): Builder {
			debounceInMs = debounce
			return this
		}

		/**
		 * An instance of [Cancellation] to cancels callbacks with the bleScanner
		 */
		fun cancellation(cancellation: Cancellation): Builder {
			this.cancellation = cancellation
			return this
		}

		/**
		 * A list of [ScanFilter] to filter devices shown on this data
		 */
		fun filters(filters: List<ScanFilter>): Builder {
			this.filters = filters
			return this
		}

		/**
		 * A handler for the delayed execution of time-out devices. (ideally the same used to instantiate the BleScanner)
		 */
		fun handler(handler: Handler): Builder {
			this.handler = handler
			return this
		}

		/**
		 * A factory to create MotionlessAverage instances to average the RSSI.
		 * If none is supplied [BleScanResult.averageRssi] will be always equals to the received RSSI
		 */
		fun averagerFactory(averagerFactory: AveragerFactory): Builder {
			this.averagerFactory = averagerFactory
			return this
		}

		/**
		 * Builds the observable data.
		 */
		fun build(): ObservableData<List<BleScanResult>> {
			val result = ObservableBleScanResult(timeoutInMs, onActiveTimeoutInMs, debounceInMs, averagerFactory, filters, lazyHandler())
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