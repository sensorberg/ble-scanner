package com.sensorberg.libs.ble.scanner.internal

import no.nordicsemi.android.support.v18.scanner.ScanCallback

interface DelayedScanner {
	fun isStarted(): Boolean
	fun start(delay: Long)
	fun stop(delay: Long)
	fun setScanCallback(callback: ScanCallback)
}