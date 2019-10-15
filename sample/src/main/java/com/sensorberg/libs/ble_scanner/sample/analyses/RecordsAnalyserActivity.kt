package com.sensorberg.libs.ble_scanner.sample.analyses

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.majorik.sparklinelibrary.SparkLineLayout
import com.sensorberg.libs.ble_scanner.sample.R
import kotlinx.android.synthetic.main.activity_analyser.*

class RecordsAnalyserActivity : AppCompatActivity() {

	private var popup: PopupWindow? = null
	private val recordingOptions = mutableListOf<RecordsAnalyserViewModel.RecordOptions>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_analyser)

		val toolbar: Toolbar = findViewById(R.id.toolbar)
		val original: SparkLineLayout = findViewById(R.id.graphOriginal)
		val constFilter: SparkLineLayout = findViewById(R.id.graphConstantFilter)
		val kalman: SparkLineLayout = findViewById(R.id.graphKalman)

		val model = ViewModelProviders.of(this)
				.get(RecordsAnalyserViewModel::class.java)

		model.original.observe(this, GraphObserver(original))
		model.constFilter.observe(this, GraphObserver(constFilter))
		model.kalman.observe(this, GraphObserver(kalman))

		model.options.observe(this, Observer {
			recordingOptions.clear()
			it?.let { recordingOptions.addAll(it) }
		})

		model.selected.observe(this, Observer { toolbar.title = it?.text })

		createMenu("Filter", ::popupFilter)
		createMenu("Kalman", ::popupKalman)
		createMenu("Batch", ::popupChooser)
	}

	private fun createMenu(text: String, onClick: () -> Unit) {
		with(toolbar.menu.add(text)) {
			setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
			setOnMenuItemClickListener {
				onClick.invoke()
				true
			}
		}
	}

	override fun onDestroy() {
		popup?.dismiss()
		popup = null
		super.onDestroy()
	}

	private fun popupChooser() {
		val recycler = RecyclerView(this)
		recycler.layoutManager = LinearLayoutManager(this)
		recycler.adapter = Adapter(recordingOptions)
		recycler.layoutParams = ViewGroup.LayoutParams((toolbar.width.toFloat() * 0.7f).toInt(), toolbar.height * 5)
		popupView(recycler)
	}

	@SuppressLint("SetTextI18n")
	private fun popupFilter() {
		val model = ViewModelProviders.of(this)
				.get(RecordsAnalyserViewModel::class.java)
		val sliders = LayoutInflater.from(this)
				.inflate(R.layout.activity_analyser_sliders_filter, null, false)

		val cfText: TextView = sliders.findViewById(R.id.constFilterText)
		fun updateText() {
			cfText.text = "K: ${model.getConstantFilterParam()}"
		}
		updateText()
		val cfSeekBar: SeekBar = sliders.findViewById(R.id.constFilter)
		cfSeekBar.setOnProgressChanged { _, percent, _ ->
			model.setConstFilterParams(percent.toFloat())
			updateText()
		}
		popupView(sliders)
	}

	private fun popupKalman() {
		val model = ViewModelProviders.of(this)
				.get(RecordsAnalyserViewModel::class.java)
		val sliders = LayoutInflater.from(this)
				.inflate(R.layout.activity_analyser_sliders_kalman, null, false)

		val rText: TextView = sliders.findViewById(R.id.kalmanRtext)
		val rSeekBar: SeekBar = sliders.findViewById(R.id.kalmanR)
		val qText: TextView = sliders.findViewById(R.id.kalmanQtext)
		val qSeekBar: SeekBar = sliders.findViewById(R.id.kalmanQ)

		fun updateText() {
			val kalmanParam = model.getKalmanParam()
			rText.text = "r: ${kalmanParam.r}"
			qText.text = "q: ${kalmanParam.q}"
		}
		updateText()

		rSeekBar.setOnProgressChanged { _, percent, _ ->
			model.setKalmanRparam(percent.toFloat())
			updateText()
		}
		qSeekBar.setOnProgressChanged { _, percent, _ ->
			model.setKalmanQparam(percent.toFloat())
			updateText()
		}

		popupView(sliders)
	}

	private fun popupView(view: View) {
		popup?.dismiss()
		popup = PopupWindow(view, (toolbar.width.toFloat() * 0.7f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
		popup?.setBackgroundDrawable(ColorDrawable(Color.GRAY))
		popup?.isOutsideTouchable = true
		popup?.showAsDropDown(toolbar, 0, 0, GravityCompat.END)
	}

	private class GraphObserver(private val graph: SparkLineLayout) : Observer<List<Int>> {
		override fun onChanged(t: List<Int>?) {
			if (t != null) {
				graph.setData(ArrayList(t))
			} else {
				graph.setData(ArrayList())
			}
			graph.invalidate()
		}
	}

	inner class Adapter(options: List<RecordsAnalyserViewModel.RecordOptions>) : ListAdapter<RecordsAnalyserViewModel.RecordOptions, Holder>(
			RecordsAnalyserViewModel.RECORD_DIFF) {

		init {
			submitList(options)
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
			val pad = (parent.context.resources.displayMetrics.density * 16).toInt()
			return Holder(TextView(parent.context).apply { setPadding(pad, pad, pad, pad) })
		}

		override fun onBindViewHolder(holder: Holder, position: Int) {
			val item = getItem(position)
			(holder.itemView as TextView).text = item.text
			holder.option = item
		}
	}

	inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

		lateinit var option: RecordsAnalyserViewModel.RecordOptions

		init {
			itemView.setOnClickListener {
				val model = ViewModelProviders.of(this@RecordsAnalyserActivity)
						.get(RecordsAnalyserViewModel::class.java)
				model.select(option)
			}
		}
	}

	internal fun SeekBar.setOnProgressChanged(listener: (seekbar: SeekBar, percent: Int, fromUser: Boolean) -> Unit) {
		setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekbar: SeekBar, percent: Int, fromUser: Boolean) {
				listener.invoke(seekbar, percent, fromUser)
			}

			override fun onStartTrackingTouch(p0: SeekBar?) {
				// not used
			}

			override fun onStopTrackingTouch(p0: SeekBar?) {
				// not used
			}

		})
	}
}