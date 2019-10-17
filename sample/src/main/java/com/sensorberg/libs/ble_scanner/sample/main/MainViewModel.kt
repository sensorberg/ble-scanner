package com.sensorberg.libs.ble_scanner.sample.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil
import com.sensorberg.libs.ble.scanner.extensions.AveragerFactory
import com.sensorberg.libs.ble.scanner.extensions.ObservableBleScanResult
import com.sensorberg.libs.ble_scanner.sample.backgroundHandler
import com.sensorberg.libs.ble_scanner.sample.bleScanner
import com.sensorberg.motionlessaverage.MotionlessAverage
import com.sensorberg.observable.Cancellation
import com.sensorberg.observable.Transformations

private const val SEVEN_SECONDS = 7_003L
private const val TWELVE_SECONDS = 12_002L
private const val ONE_SECOND = 1_001L
private val averagerFactory: AveragerFactory = { MotionlessAverage.Builder.createConstantFilterAverage(10f) }

class MainViewModel(application: Application) : AndroidViewModel(application) {

	private val cancellation = Cancellation()

	val title: LiveData<String>
	val subtitle: LiveData<String>
	val data: LiveData<List<ScanData>>

	init {

		val observableBleScans = ObservableBleScanResult
				.builder(bleScanner)
				.timeoutInMs(SEVEN_SECONDS)
				.onActiveTimeoutInMs(TWELVE_SECONDS)
				.debounceInMs(ONE_SECOND)
				.cancellation(cancellation)
				.handler(backgroundHandler)
				.averagerFactory(averagerFactory)
				.build()

		val transformedScans = Transformations.map(observableBleScans) { list ->
			list?.map {
				ScanData(
						text = "${it.scanResult.device.name}/${it.scanResult.device.address} :: ${it.averageRssi}dB",
						id = it.scanResult.device.address,
						address = it.scanResult.device.address)
			}
		}
		data = transformedScans.toLiveData()
		title = Transformations
				.map(bleScanner.getState()) { it?.toString() }
				.toLiveData()
		subtitle = Transformations
				.map(transformedScans) { "Found ${it?.size ?: 0} devices" }
				.toLiveData()
	}

	override fun onCleared() {
		cancellation.cancel()
	}

	class ScanData(val text: String, val id: String, val address: String)

	companion object {
		val SCAN_DATA_DIFF = object : DiffUtil.ItemCallback<ScanData>() {
			override fun areItemsTheSame(oldItem: ScanData, newItem: ScanData): Boolean {
				return oldItem.id == newItem.id
			}

			override fun areContentsTheSame(oldItem: ScanData, newItem: ScanData): Boolean {
				return oldItem.id == newItem.id && oldItem.text == newItem.text
			}
		}
	}
}