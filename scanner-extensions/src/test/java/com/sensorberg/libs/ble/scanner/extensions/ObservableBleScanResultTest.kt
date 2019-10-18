package com.sensorberg.libs.ble.scanner.extensions

import android.bluetooth.BluetoothDevice
import android.os.Handler
import com.sensorberg.executioner.testing.ExecutionerTestRule
import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.libs.time.Time
import com.sensorberg.libs.time.test.JvmTimeTestRule
import com.sensorberg.libs.time.test.injectTime
import com.sensorberg.motionlessaverage.MotionlessAverage
import com.sensorberg.observable.Cancellation
import com.sensorberg.observable.MutableObservableData
import com.sensorberg.observable.ObservableData
import com.sensorberg.observable.Observer
import io.mockk.*
import no.nordicsemi.android.support.v18.scanner.ScanResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val debounceInMs = 1000L
private const val onActiveTimeoutInMs = 14000L
private const val timeoutInMs = 7000L

class ObservableBleScanResultTest {

	@get:Rule val executionerTestRule = ExecutionerTestRule()
	@get:Rule val jvmTimeTestRule = JvmTimeTestRule()

	private lateinit var handlerRunnable: CapturingSlot<Runnable>
	private lateinit var handlerDelayMillis: CapturingSlot<Long>
	private lateinit var handler: Handler
	private lateinit var bleScanner: BleScanner
	private lateinit var bleScannerCallback: CapturingSlot<ScanResultCallback>
	private lateinit var cancellation: Cancellation
	private lateinit var bleScannerState: MutableObservableData<BleScanner.State>
	private lateinit var tested: ObservableData<List<BleScanResult>>
	private lateinit var observer: Observer<List<BleScanResult>>

	@Before
	fun setUp() {
		cancellation = Cancellation()
		handlerRunnable = slot()
		handlerDelayMillis = slot()
		bleScannerCallback = slot()
		bleScannerState = MutableObservableData()
		bleScanner = mockk(relaxed = true)
		every { bleScanner.addCallback(capture(bleScannerCallback)) } just Runs
		every { bleScanner.removeCallback(any()) } answers { bleScannerCallback.clear() }
		every { bleScanner.getState() } returns bleScannerState
		handler = mockk()
		every { handler.postDelayed(capture(handlerRunnable), capture(handlerDelayMillis)) } returns true
		every { handler.removeCallbacks(any()) } just Runs

		observer = { }

		tested = ObservableBleScanResult.builder(bleScanner)
				.debounceInMs(debounceInMs)
				.onActiveTimeoutInMs(onActiveTimeoutInMs)
				.timeoutInMs(timeoutInMs)
				.cancellation(cancellation)
				.handler(handler)
				.averagerFactory { MotionlessAverage.Builder.createConstantFilterAverage(2f) }
				.build()
	}

	@Test fun `when build, callback and state gets observed`() {
		assertTrue(bleScannerCallback.isCaptured)
		verify(exactly = 1) { bleScanner.getState() }
		assertTrue(bleScannerState.hasObservers())
	}

	@Test fun `when cancel, callback and state gets removed`() {
		cancellation.cancel()
		assertFalse(bleScannerCallback.isCaptured)
		assertFalse(bleScannerState.hasObservers())
		assertNull(tested.value)
	}

	@Test fun `when several scans received, data is debounced`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -51)
			assertEquals(1, tested.value?.size)
			assertEquals("device-1", tested.value!![0].scanResult.device.address)

			elapsedTimeResponse = startTime + (debounceInMs / 2)
			sendScan("device-2", -52)
			sendScan("device-3", -53)
			sendScan("device-4", -54)
			sendScan("device-1", -61)

			// data got debounced
			assertEquals(1, tested.value?.size)
			assertEquals("device-1", tested.value!![0].scanResult.device.address)

			// time has passed
			elapsedTimeResponse = startTime + debounceInMs + 1
			sendScan("device-1", -62)
			assertEquals(4, tested.value?.size)
			assertEquals("device-1", tested.value!![3].scanResult.device.address)
			assertEquals(-62, tested.value!![3].scanResult.rssi)
		}
	}

	@Test fun `check all behaviors of 1-off scan received`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -50)

			// data updated
			assertEquals(1, tested.value?.size)
			assertEquals("device-1", tested.value!![0].scanResult.device.address)

			// scheduled for removal
			verify(exactly = 1) { handler.postDelayed(any(), any()) }
			assertTrue(handlerRunnable.isCaptured)
			assertTrue(handlerDelayMillis.isCaptured)

			// grab the runnable and the delay and clear the captures
			val run1 = handlerRunnable.captured
			val run1Delay = handlerDelayMillis.captured
			handlerRunnable.clear()
			handlerDelayMillis.clear()

			// not enough time has passed and executes the runnable
			elapsedTimeResponse = startTime + run1Delay
			run1.run()

			// data is still there
			assertEquals(1, tested.value?.size)
			assertEquals("device-1", tested.value!![0].scanResult.device.address)

			// it was re-scheduled for removal
			verify(exactly = 2) { handler.postDelayed(any(), any()) }
			assertTrue(handlerRunnable.isCaptured)
			assertTrue(handlerDelayMillis.isCaptured)

			// enough time has passed and the runnable executes
			val run2 = handlerRunnable.captured
			handlerRunnable.clear()
			handlerDelayMillis.clear()
			elapsedTimeResponse = startTime + timeoutInMs + 1
			run2.run()

			// data was cleared
			assertEquals(0, tested.value?.size)

			// nothing is re-scheduled
			assertFalse(handlerRunnable.isCaptured)
			assertFalse(handlerDelayMillis.isCaptured)
		}
	}

	@Test fun `rssi values are averaged`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -50)
			sendScan("device-2", -150)
			assertEquals((-50).toFloat(), tested.value!![0].averageRssi)

			sendScan("device-1", -40) // average to 45
			sendScan("device-2", -140)
			sendScan("device-1", -35) // average to 40
			sendScan("device-2", -135)
			sendScan("device-1", -38) // average to 39
			sendScan("device-2", -138)

			sendScan("device-1", -36) // average to 37.5
			elapsedTimeResponse = startTime + debounceInMs + 1
			sendScan("device-2", -136)

			assertEquals(-37.5f, tested.value!![0].averageRssi)
			assertEquals(-137.5f, tested.value!![1].averageRssi)
		}
	}

	@Test fun `values are ordered by rssi`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-3", -37)
			sendScan("device-2", -35)
			sendScan("device-6", -87)
			sendScan("device-4", -42)
			sendScan("device-1", -23)
			elapsedTimeResponse = startTime + debounceInMs + 1
			sendScan("device-5", -55)

			val value = tested.value!!
			assertEquals(6, value.size)
			assertEquals("device-1", value[0].scanResult.device.address)
			assertEquals("device-2", value[1].scanResult.device.address)
			assertEquals("device-3", value[2].scanResult.device.address)
			assertEquals("device-4", value[3].scanResult.device.address)
			assertEquals("device-5", value[4].scanResult.device.address)
			assertEquals("device-6", value[5].scanResult.device.address)
		}
	}

	@Test fun `if scans received while not observing, update values when active`() {
		sendScan("device-1", -42)
		sendScan("device-2", -52)
		tested.setActive()
		assertEquals(2, tested.value?.size)
	}

	@Test fun `if scans received while not observing, update values when active, part 2`() {
		tested.setActive()
		sendScan("device-1", -42)
		tested.setInactive()
		sendScan("device-2", -52)
		sendScan("device-1", -32)
		tested.setActive()
		assertEquals(2, tested.value?.size)
	}

	@Test fun `on active, very old devices are removed`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -50)
			tested.setInactive()
			elapsedTimeResponse = startTime + onActiveTimeoutInMs + 1
			assertEquals(1, tested.value?.size)
			tested.setActive()
			assertEquals(0, tested.value?.size)
		}
	}

	@Test fun `when scanner state is BLUETOOTH_DISABLED, devices are cleared`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -50)
			bleScannerState.value = BleScanner.State.IDLE(BleScanner.Reason.BLUETOOTH_DISABLED)
			assertEquals(0, tested.value?.size)
		}
	}

	@Test fun `when scanner state is LOCATION_DISABLED, devices are cleared`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -50)
			bleScannerState.value = BleScanner.State.IDLE(BleScanner.Reason.LOCATION_DISABLED)
			assertEquals(0, tested.value?.size)
		}
	}

	@Test fun `when scanner state is NO_LOCATION_PERMISSION, devices are cleared`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -50)
			bleScannerState.value = BleScanner.State.IDLE(BleScanner.Reason.NO_LOCATION_PERMISSION)
			assertEquals(0, tested.value?.size)
		}
	}

	@Test fun `when scanner state is ERROR, devices are cleared`() {
		val startTime = 1000L
		injectTime {
			elapsedTimeResponse = startTime
			tested.setActive()
			sendScan("device-1", -50)
			bleScannerState.value = BleScanner.State.ERROR(42)
			assertEquals(0, tested.value?.size)
		}
	}

	private fun ObservableData<List<BleScanResult>>.setActive() {
		observe(observer)
	}

	private fun ObservableData<List<BleScanResult>>.setInactive() {
		removeObserver(observer)
	}

	private fun sendScan(address: String, rssi: Int) {
		val scan = mockScanResult(address, rssi)
		bleScannerCallback.captured.onScanResult(scan)
	}

	private fun mockScanResult(address: String, rssi: Int): ScanResult {

		val device: BluetoothDevice = mockk()
		every { device.address } returns address
		val scanResult: ScanResult = mockk()
		every { scanResult.device } returns device
		every { scanResult.rssi } returns rssi
		val timestampNanos = Time.getElapsedTime() * 1_000_000L
		every { scanResult.timestampNanos } returns timestampNanos

		return scanResult

	}

}