package com.sensorberg.libs.ble.scanner.extensions

import com.sensorberg.executioner.testing.ExecutionerTestRule
import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.libs.time.test.JvmTimeTestRule
import com.sensorberg.observable.Cancellation
import com.sensorberg.observable.MutableObservableData
import io.mockk.*
import no.nordicsemi.android.support.v18.scanner.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class ScanResultFinderTest {

	@get:Rule val executionerTestRule = ExecutionerTestRule()
	@get:Rule val jvmTimeTestRule = JvmTimeTestRule()

	private lateinit var cancellation: Cancellation
	private lateinit var bleScanner: BleScanner
	private lateinit var bleScannerCallback: CapturingSlot<ScanResultCallback>
	private lateinit var bleScannerState: MutableObservableData<BleScanner.State>

	@Before fun setUp() {
		bleScannerCallback = slot()
		bleScannerState = MutableObservableData()
		bleScanner = mockk(relaxed = true)
		cancellation = Cancellation()
		every { bleScanner.addCallback(capture(bleScannerCallback)) } just Runs
		every { bleScanner.removeCallback(any()) } answers { bleScannerCallback.clear() }
		every { bleScanner.getState() } returns bleScannerState
	}

	@Test fun `when results available on initialSearch, do not use bleScanner`() {
		val found = AtomicReference<String>(null)
		val onScanFound: ((String) -> Unit) = { found.set(it) }
		scanResultFinder(bleScanner,
						 cancellation,
						 listOf(mockScanResult("device-1", -50)),
						 { it.device.address },
						 onScanFound)

		assertEquals("device-1", found.get())
		verify(exactly = 0) { bleScanner.addCallback(any()) }
	}

	@Test fun `when results not available on initialSearch, use bleScanner`() {
		val found = AtomicReference<String>(null)
		val onScanFound: ((String) -> Unit) = { found.set(it) }
		scanResultFinder(bleScanner,
						 cancellation,
						 listOf(mockScanResult("device-1", -50)),
						 { null },
						 onScanFound)

		assertEquals(null, found.get())
		verify(exactly = 1) { bleScanner.addCallback(any()) }
	}

	@Test fun `onCancellation, scanner callback is removed`() {
		val found = AtomicReference<String>(null)
		val onScanFound: ((String) -> Unit) = { found.set(it) }
		scanResultFinder(bleScanner,
						 cancellation,
						 listOf(),
						 { null },
						 onScanFound)

		val callback = bleScannerCallback.captured
		cancellation.cancel()
		verify(exactly = 1) { bleScanner.removeCallback(callback) }
	}

	@Test fun `mapper receives call to every scan`() {
		val received = mutableListOf<ScanResult>()
		scanResultFinder(bleScanner,
						 cancellation,
						 listOf(),
						 {
							 received.add(it)
							 null
						 },
						 {})
		val callback = bleScannerCallback.captured
		val scan1 = mockScanResult("device-1", -50)
		val scan2 = mockScanResult("device-2", -23)
		val scan3 = mockScanResult("device-1", -55)
		callback.onScanResult(scan1)
		callback.onScanResult(scan2)
		callback.onScanResult(scan3)
		assertEquals(3, received.size)
		assertEquals(scan1, received[0])
		assertEquals(scan2, received[1])
		assertEquals(scan3, received[2])
	}

	@Test fun `when mapper returns non-null, value is passed to onScanFound and scanner callback is removed`() {
		val found = AtomicReference<String>(null)
		val onScanFound: ((String) -> Unit) = { found.set(it) }
		scanResultFinder(bleScanner,
						 cancellation,
						 listOf(),
						 {
							 if (it.rssi == -55) {
								 "found"
							 } else {
								 null
							 }
						 },
						 onScanFound)
		val callback = bleScannerCallback.captured
		val scan1 = mockScanResult("device-1", -50)
		val scan2 = mockScanResult("device-2", -23)
		val scan3 = mockScanResult("device-1", -55)
		callback.onScanResult(scan1)
		callback.onScanResult(scan2)

		assertEquals(null, found.get())
		verify(exactly = 0) { bleScanner.removeCallback(callback) }

		callback.onScanResult(scan3)

		assertEquals("found", found.get())
		verify(exactly = 1) { bleScanner.removeCallback(callback) }
	}
}