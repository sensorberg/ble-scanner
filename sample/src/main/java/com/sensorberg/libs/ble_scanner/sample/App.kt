package com.sensorberg.libs.ble_scanner.sample

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import androidx.room.Room
import com.sensorberg.libs.ble.scanner.BleScanner
import com.sensorberg.libs.ble.scanner.lifecycle.scanWithProcessLifecycle
import com.sensorberg.libs.ble_scanner.sample.database.ScanDatabase
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import timber.log.Timber

lateinit var bleScanner: BleScanner
lateinit var scanDatabase: ScanDatabase

private val FILTER_SENSORBERG_GATEWAY = ScanFilter.Builder()
		.setServiceUuid(ParcelUuid.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
		.build()
private val FILTER_BLUE_ID_DEVICE = ScanFilter.Builder()
		.setServiceUuid(ParcelUuid.fromString("00005301-0000-002a-426c-756549442a00"))
		.build()

private val backgroundThread = HandlerThread("ble-scanner").apply { start() }
val backgroundHandler by lazy { Handler(backgroundThread.looper) }

class App : Application() {
	override fun onCreate() {
		super.onCreate()
		Timber.plant(Timber.DebugTree())
		scanDatabase = Room
				.databaseBuilder(this, ScanDatabase::class.java, "scan-database")
				.build()
		bleScanner = BleScanner
				.builder(this)
				.handler(backgroundHandler)
				.filters(listOf(FILTER_SENSORBERG_GATEWAY))
				.build()
		bleScanner.scanWithProcessLifecycle()
	}
}