package com.sensorberg.libs.ble_scanner.sample

import android.app.Application
import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.lifecycle.scanWithProcessLifecycle
import timber.log.Timber

lateinit var scanner: BleScanner

class App : Application() {

	override fun onCreate() {
		super.onCreate()
		Timber.plant(Timber.DebugTree())
		scanner = BleScanner.builder(this)
				.build()
		scanner.scanWithProcessLifecycle()
	}
}