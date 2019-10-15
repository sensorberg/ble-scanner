package com.sensorberg.libs.ble_scanner.sample.recorder

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.recyclerview.widget.DiffUtil
import com.sensorberg.executioner.Executioner.POOL
import com.sensorberg.executioner.Executioner.SINGLE
import com.sensorberg.executioner.Executioner.runOn
import com.sensorberg.libs.ble.scanner.ScanResultCallback
import com.sensorberg.libs.ble_scanner.sample.bleScanner
import com.sensorberg.libs.ble_scanner.sample.database.ScanDao
import com.sensorberg.libs.ble_scanner.sample.database.StoredScanBactch
import com.sensorberg.libs.ble_scanner.sample.database.StoredScanData
import com.sensorberg.libs.ble_scanner.sample.scanDatabase
import com.sensorberg.motionlessaverage.MotionlessAverage
import no.nordicsemi.android.support.v18.scanner.ScanResult
import timber.log.Timber

class RecorderViewModel(application: Application) : AndroidViewModel(application) {

	@Volatile private var averageRssi: Float = 0f
	private val averager = MotionlessAverage.Builder.createConstantFilterAverage(20f)
	private var isRecording = false
	private val dao: ScanDao = scanDatabase.scanDao()
	private lateinit var title: String
	private lateinit var address: String
	private val batchId = MutableLiveData<Long>()
	private val recordedScans: LiveData<List<StoredScanData>> = Transformations.switchMap(batchId) {
		return@switchMap if (it != null) {
			dao.getScans(it)
		} else {
			null
		}
	}

	val data: LiveData<List<UiData>> = Transformations.map(recordedScans) { list ->
		val data = mutableListOf<UiData>()
		data.add(UiData(title, 0))
		data.add(UiData(address, 1))
		data.add(UiData("Recorded ${list.size} items", 2))
		data.add(UiData("Current average is ${averageRssi}dB", 3))
		return@map data
	}

	fun record(title: String, address: String) {
		if (isRecording) {
			return
		}
		isRecording = true
		this.title = title
		this.address = address
		runOn(SINGLE) {
			val newId = dao.newBatch(StoredScanBactch(0, title))
			batchId.postValue(newId)
			Timber.d("Starting record of batch $title. Batch ID = $newId")
			bleScanner.addCallback(scanResultCallback)
		}
	}

	override fun onCleared() {
		bleScanner.removeCallback(scanResultCallback)
	}

	private val scanResultCallback = object : ScanResultCallback {
		override fun onScanResult(scanResult: ScanResult) {
			val batchId = this@RecorderViewModel.batchId.value
			if (batchId == null) {
				return
			}
			if (scanResult.device.address != address) {
				return
			}
			averageRssi = averager.average(scanResult.rssi.toFloat())
			runOn(POOL) {
				Timber.v("Adding scan data to ${scanResult.rssi}")
				dao.newScan(StoredScanData(scanResult.timestampNanos, batchId, scanResult.rssi))
			}
		}
	}

	class UiData(val text: String, val id: Long)

	private fun nanoToEpochMillis(nanos: Long): Long {
		return System.currentTimeMillis() - SystemClock.elapsedRealtime() + nanos / 1_000_000L
	}

	companion object {
		val SCAN_DATA_DIFF = object : DiffUtil.ItemCallback<UiData>() {
			override fun areItemsTheSame(oldItem: UiData, newItem: UiData): Boolean {
				return oldItem.id == newItem.id
			}

			override fun areContentsTheSame(oldItem: UiData, newItem: UiData): Boolean {
				return oldItem.id == newItem.id && oldItem.text == newItem.text
			}
		}
	}
}