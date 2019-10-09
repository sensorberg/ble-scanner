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

	private fun update(bluetoothEnabled: Boolean,
					   locationEnabled: Boolean,
					   locationPermission: Boolean,
					   scanRequested: BleScannerStateController.ScanRequest) {
		tested.update(bluetoothEnabled, locationEnabled, locationPermission, scanRequested)
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
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = SCAN)
	}

	@Test fun `does scan when requested and all permissions passed`() {
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = SCAN)
		assertEquals(BleScanner.State.SCANNING, state)
		verifyStartScan(1)
	}

	@Test fun `does not scan without location permission`() {
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = false,
			   scanRequested = SCAN)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.NO_LOCATION_PERMISSION), state)
		verifyStartScan(0)
	}

	@Test fun `does not scan with bluetooth off`() {
		update(bluetoothEnabled = false,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = SCAN)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.BLUETOOTH_DISABLED), state)
		verifyStartScan(0)
	}

	@Test fun `does not scan when requested to stop now`() {
		startScan()
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = STOP_NOW)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.NO_SCAN_REQUEST), state)
		verifyStartScan(1)
		verifyStopScan(1)
	}

	@Test fun `does not scan when requested to stop delayed`() {
		startScan()
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = STOP_DELAYED)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.NO_SCAN_REQUEST), state)
		verifyStartScan(1)
		verifyStopScan(1)
	}

	@Test fun `does scan with location off but reports as idle`() {
		update(bluetoothEnabled = true,
			   locationEnabled = false,
			   locationPermission = true,
			   scanRequested = SCAN)
		assertEquals(BleScanner.State.IDLE(BleScanner.Reason.LOCATION_DISABLED), state)
		verifyStartScan(1)
	}

	@Test fun `on bluetooth error restarts scans`() {
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStartScan(1)
		tested.setBluetoothError(42)
		assertEquals(BleScanner.State.ERROR(42), state)
		verifyStopScan(1)
		verifyStartScan(2)
	}

	@Test fun `does not stop scan when location is off`() {
		verifyStartScan(0)
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStartScan(1)
		update(bluetoothEnabled = true,
			   locationEnabled = false,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStartScan(2)
		update(bluetoothEnabled = true,
			   locationEnabled = true,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStartScan(3)
		verifyStopScan(0)
	}

	@Test fun `starts scanning after getting permission even if location is off`() {
		update(bluetoothEnabled = true,
			   locationEnabled = false,
			   locationPermission = false,
			   scanRequested = SCAN)
		verifyStartScan(0)
		update(bluetoothEnabled = true,
			   locationEnabled = false,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStartScan(1)
	}

	@Test fun `restart is called when bluetooth goes off and back on`() {
		update(bluetoothEnabled = true,
			   locationEnabled = false,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStartScan(1)
		verifyStopScan(0)
		update(bluetoothEnabled = false,
			   locationEnabled = false,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStartScan(1)
		verifyStopScan(0)
		update(bluetoothEnabled = true,
			   locationEnabled = false,
			   locationPermission = true,
			   scanRequested = SCAN)
		verifyStopScan(1)
		verifyStartScan(2)
	}
}