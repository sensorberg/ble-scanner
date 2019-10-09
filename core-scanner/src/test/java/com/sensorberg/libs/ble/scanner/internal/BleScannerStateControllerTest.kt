package com.sensorberg.libs.ble.scanner.internal

import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.internal.BleScannerStateController.ScanRequest.*
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class BleScannerStateControllerTest {
	private lateinit var tested: BleScannerStateController
	private lateinit var state: BleScanner.State

	private lateinit var delayedScanner: DelayedScanner
	private lateinit var startDelay: CapturingSlot<Long>
	private lateinit var stopDelay: CapturingSlot<Long>

	@Before fun setUp() {
		val isStarted = AtomicBoolean(false)
		delayedScanner = mockk()
		startDelay = slot()
		stopDelay = slot()
		every { delayedScanner.start(capture(startDelay)) } answers { isStarted.set(true) }
		every { delayedScanner.stop(capture(stopDelay)) } answers { isStarted.set(false) }
		every { delayedScanner.isStarted() } answers { isStarted.get() }

		tested = BleScannerStateController(delayedScanner, null)
		tested.onStateChanged { state = it }
	}

	private fun verifyStartScan(count: Int) {
		require(count >= 0) { "You're an idiot!" }
		verify(exactly = count) { delayedScanner.start(any()) }
	}

	private fun verifyStopScan(count: Int) {
		require(count >= 0) { "You're an idiot!" }
		verify(exactly = count) { delayedScanner.stop(any()) }
	}

	private fun startScan() {
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = SCAN)
	}

	@Test fun `does scan when requested and all permissions passed`() {
		startScan()
		assertEquals(BleScanner.State.SCANNING, state)
		verifyStartScan(1)
	}

	@Test fun `does not scan without location permission`() {
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = false,
					  scanRequest = SCAN)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.NO_LOCATION_PERMISSION), state)
		verifyStartScan(0)
	}

	@Test fun `does not scan with bluetooth off`() {
		tested.update(bluetoothEnabled = false,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = SCAN)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.BLUETOOTH_DISABLED), state)
		verifyStartScan(0)
	}

	@Test fun `does not scan when requested to stop now`() {
		startScan()
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = STOP_NOW)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.NO_SCAN_REQUEST), state)
		verifyStartScan(1)
		verifyStopScan(1)
	}

	@Test fun `does not scan when requested to stop delayed`() {
		startScan()
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = STOP_DELAYED)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.NO_SCAN_REQUEST), state)
		verifyStartScan(1)
		verifyStopScan(1)
	}

	@Test fun `does scan with location off but reports as idle`() {
		tested.update(bluetoothEnabled = true,
					  locationEnabled = false,
					  locationPermission = true,
					  scanRequest = SCAN)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.LOCATION_DISABLED), state)
		verifyStartScan(1)
	}

	@Test fun `on bluetooth error restarts scans`() {
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = SCAN)
		verifyStartScan(1)
		tested.setBluetoothError(42)
		assertEquals(BleScanner.State.ERROR(42), state)
		verifyStopScan(1)
		verifyStartScan(2)
	}

	@Test fun `does not stop scan when location is off`() {
		verifyStartScan(0)
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = SCAN)
		verifyStartScan(1)
		tested.update(bluetoothEnabled = true,
					  locationEnabled = false,
					  locationPermission = true,
					  scanRequest = SCAN)
		verifyStartScan(2)
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = SCAN)
		verifyStartScan(3)
		verifyStopScan(0)
	}

	@Test fun `starts scanning after getting permission even if location is off`() {
		tested.update(bluetoothEnabled = true,
					  locationEnabled = false,
					  locationPermission = false,
					  scanRequest = SCAN)
		verifyStartScan(0)
		tested.update(bluetoothEnabled = true,
					  locationEnabled = false,
					  locationPermission = true,
					  scanRequest = SCAN)
		verifyStartScan(1)
	}

	@Test fun `keeps scanning when location goes off`() {
		startScan()
		verifyStartScan(1)
		tested.update(bluetoothEnabled = true,
					  locationEnabled = false,
					  locationPermission = true,
					  scanRequest = SCAN)
		verifyStopScan(0)
	}

	@Test fun `stop scan when bluetooth goes off`() {
		startScan()
		verifyStartScan(1)
		tested.update(bluetoothEnabled = false,
					  locationEnabled = true,
					  locationPermission = true,
					  scanRequest = SCAN)
		verifyStopScan(1)
	}

	@Test fun `stop scan when location permission goes off`() {
		startScan()
		verifyStartScan(1)
		tested.update(bluetoothEnabled = true,
					  locationEnabled = true,
					  locationPermission = false,
					  scanRequest = SCAN)
		verifyStopScan(1)
	}
}