package com.sensorberg.libs.ble_scanner.sample.analyses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.recyclerview.widget.DiffUtil
import com.github.mikephil.charting.data.Entry
import com.sensorberg.executioner.Executioner.SINGLE
import com.sensorberg.executioner.Executioner.runOn
import com.sensorberg.libs.ble_scanner.sample.database.ScanDao
import com.sensorberg.libs.ble_scanner.sample.database.StoredScanData
import com.sensorberg.libs.ble_scanner.sample.scanDatabase
import com.sensorberg.motionlessaverage.MotionlessAverage
import timber.log.Timber
import kotlin.random.Random

private const val USE_TEST_DATA = true

class RecordsAnalyserViewModel(application: Application) : AndroidViewModel(application) {

	private val dao: ScanDao = scanDatabase.scanDao()

	val original: LiveData<List<Entry>>
	val constFilter: LiveData<List<Entry>>
	val kalman: LiveData<List<Entry>>

	private val constFilterParam = MutableLiveData<Float>().apply { value = 1f }
	private val kalmanFilterParam = MutableLiveData<KalmanParam>().apply { value = KalmanParam(0.05f, 1f) }

	val options: LiveData<List<RecordOptions>> = MutableLiveData()
	val selected: LiveData<RecordOptions> = MutableLiveData()

	init {
		runOn(SINGLE) {
			val data = dao.getBatchList()
					.map { RecordOptions(it.title, it.id) }
			(options as MutableLiveData).postValue(data)
			if (data.isNotEmpty()) {
				(selected as MutableLiveData).postValue(data[0])
			}
		}

		if (USE_TEST_DATA) {
			original = MutableLiveData()
			refreshTestData()
		} else {
			val scans: LiveData<List<StoredScanData>> = Transformations.switchMap(selected) {
				it?.let { dao.getScans(it.id) }
			}
			original = Transformations.map(scans) {
				it?.map { Entry(it.timestamp.toFloat(), it.rssi.toFloat()) }
			}
		}

		constFilter = Transformations.switchMap(constFilterParam) { filter ->
			Timber.d("New constant filter value $filter")
			val returnListData: LiveData<List<Entry>> = Transformations.map(original) { nullableList ->
				nullableList?.let { list ->
					val avg = MotionlessAverage.Builder.createConstantFilterAverage(filter ?: 1f)
					list.map { entry ->
						val value: Float = avg.average(entry.y)
						Entry(entry.x, value)
					}
				}
			}
			returnListData
		}

		kalman = Transformations.switchMap(kalmanFilterParam) { params ->
			Timber.d("New Kalman filter params $params")
			val returnListData: LiveData<List<Entry>> = Transformations.map(original) { nullableList ->
				nullableList?.let { list ->
					val avg = MotionlessAverage.Builder.createSimplifiedKalmanFilter(params?.r ?: 0.05f, params?.q ?: 1f)
					list.map { entry ->
						val value: Float = avg.average(entry.y)
						Entry(entry.x, value)
					}
				}
			}
			returnListData
		}
	}

	fun select(selection: RecordOptions) {
		(selected as MutableLiveData).value = selection
	}

	fun getKalmanParam(): KalmanParam {
		return kalmanFilterParam.value!!
	}

	fun setKalmanRparam(r: Float) {
		val newR = (r / 100f) + 0.05f
		kalmanFilterParam.value = getKalmanParam().copy(r = newR)
	}

	fun setKalmanQparam(q: Float) {
		val newQ = (q / 3f) + 1f
		kalmanFilterParam.value = getKalmanParam().copy(q = newQ)
	}

	fun getConstantFilterParam(): Float {
		return constFilterParam.value!!
	}

	fun setConstFilterParams(filter: Float) {
		if (filter < 1f) {
			constFilterParam.value = 1f
		} else {
			constFilterParam.value = filter
		}
	}

	data class KalmanParam(val r: Float, val q: Float)
	class RecordOptions(val text: String, val id: Long)

	companion object {
		val RECORD_DIFF = object : DiffUtil.ItemCallback<RecordOptions>() {
			override fun areItemsTheSame(oldItem: RecordOptions, newItem: RecordOptions): Boolean {
				return oldItem.id == newItem.id
			}

			override fun areContentsTheSame(oldItem: RecordOptions, newItem: RecordOptions): Boolean {
				return oldItem.id == newItem.id && oldItem.text == newItem.text
			}
		}
	}

	fun Random.nextFloat(min: Float, max: Float): Float {
		return Random.nextDouble(min.toDouble(), max.toDouble())
				.toFloat()
	}

	fun refreshTestData() {
		if (USE_TEST_DATA) {
			var lastRssi = Random.nextFloat(-100f, -10f)
			var lastTime = 10f
			val testData = mutableListOf<Entry>()
			repeat(100) {
				lastRssi += Random.nextFloat(-10f, 10f)
				lastTime += Random.nextFloat(50f, 900f)
				testData.add(Entry(lastTime, lastRssi))
			}
			(original as MutableLiveData).value = testData
		}
	}
}