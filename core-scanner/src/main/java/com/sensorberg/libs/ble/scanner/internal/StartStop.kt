package com.sensorberg.libs.ble.scanner.internal

import java.util.concurrent.atomic.AtomicBoolean

class StartStop {

	private val _isStarted = AtomicBoolean(false)

	fun isStarted() = _isStarted.get()

	fun start(run: () -> Unit) {
		if (_isStarted.compareAndSet(false, true)) {
			run.invoke()
		}
	}

	fun stop(run: () -> Unit) {
		if (_isStarted.compareAndSet(true, false)) {
			run.invoke()
		}
	}

}