package com.sensorberg.libs.ble_scanner.sample

import android.app.Application
import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.lifecycle.scanWithProcessLifecycle
import timber.log.Timber

lateinit var bleScanner: BleScanner

class App : Application() {

	override fun onCreate() {
		super.onCreate()
		Timber.plant(Timber.DebugTree())
		bleScanner = BleScanner.builder(this)
				.build()
		bleScanner.scanWithProcessLifecycle()
	}
}