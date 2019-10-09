package com.sensorberg.libs.ble_scanner.sample

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sensorberg.libs.ble.scanner.extensions.ObservableDataScanResult
import com.sensorberg.observable.Cancellation
import com.sensorberg.observable.Transformations
import com.sensorberg.permissionbitte.PermissionBitte

class MainActivity : AppCompatActivity() {

	private lateinit var cancel: Cancellation

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		if (PermissionBitte.shouldAsk(this)) {
			PermissionBitte.ask(this, null)
		}

		val toolbar: Toolbar = findViewById(R.id.toolbar)
		val recycler: RecyclerView = findViewById(R.id.recycler)
		val adapter = Adapter()

		recycler.layoutManager = LinearLayoutManager(this)
		recycler.adapter = adapter

		cancel = Cancellation()
		val data = ObservableDataScanResult.builder(scanner)
				.cancellation(cancel)
				.build()
		val stringData = Transformations.map(data) {
			it?.map { "${it.scanResult.device.name}/${it.scanResult.device.address} :: ${it.averageRssi}dB" }
		}

		var state = ""
		var count = 0

		fun updateToolbar() {
			toolbar.title = state
			toolbar.subtitle = "Found $count devices"
		}

		scanner.getState()
				.toLiveData()
				.observe(this, Observer {
					state = it?.toString() ?: ""
					updateToolbar()
				})
		stringData
				.toLiveData()
				.observe(this, Observer {
					count = it?.size ?: 0
					updateToolbar()
					adapter.submitList(it)
				})
	}

	override fun onDestroy() {
		cancel.cancel()
		super.onDestroy()
	}

	class Adapter : ListAdapter<String, Holder>(diff) {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
			val pad = (parent.context.resources.displayMetrics.density * 16).toInt()
			return Holder(TextView(parent.context).apply { setPadding(pad, pad, pad, pad) })
		}

		override fun onBindViewHolder(holder: Holder, position: Int) {
			(holder.itemView as TextView).text = getItem(position)
		}
	}

	class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

	companion object {
		val diff = object : DiffUtil.ItemCallback<String>() {
			override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
				return oldItem == newItem
			}

			override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
				return oldItem == newItem
			}
		}
	}
}
