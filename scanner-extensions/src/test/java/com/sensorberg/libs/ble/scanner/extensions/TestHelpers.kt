package com.sensorberg.libs.ble.scanner.extensions

import android.bluetooth.BluetoothDevice
import com.sensorberg.libs.time.Time
import io.mockk.every
import io.mockk.mockk
import no.nordicsemi.android.support.v18.scanner.ScanResult

internal fun mockScanResult(address: String, rssi: Int): ScanResult {

	val device: BluetoothDevice = mockk()
	every { device.address } returns address
	val scanResult: ScanResult = mockk()
	every { scanResult.device } returns device
	every { scanResult.rssi } returns rssi
	val timestampNanos = Time.getElapsedTime() * 1_000_000L
	every { scanResult.timestampNanos } returns timestampNanos

	return scanResult

}