package com.sensorberg.libs.ble_scanner.sample.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sensorberg.libs.ble_scanner.sample.R
import com.sensorberg.permissionbitte.PermissionBitte

class MainActivity : AppCompatActivity() {

	private var isPaused = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// can I haz ur location
		if (PermissionBitte.shouldAsk(this)) {
			PermissionBitte.ask(this, null)
		}

		// view setup
		setContentView(R.layout.activity_main)

		val fab: FloatingActionButton = findViewById(R.id.fab)
		val toolbar: Toolbar = findViewById(R.id.toolbar)
		val recycler: RecyclerView = findViewById(R.id.recycler)
		val adapter = Adapter()
		recycler.layoutManager = LinearLayoutManager(this)
		recycler.adapter = adapter

		// data connection
		val model = ViewModelProviders.of(this)
				.get(MainViewModel::class.java)

		model.title.observe(this, Observer { toolbar.title = it })
		model.subtitle.observe(this, Observer { toolbar.subtitle = it })
		model.data.observe(this, Observer {
			if (isPaused) {
				return@Observer
			}
			adapter.submitList(it)
		})
		fab.setOnClickListener {
			isPaused = !isPaused
			if (isPaused) {
				fab.setImageResource(R.drawable.ic_play)
			} else {
				fab.setImageResource(R.drawable.ic_pause)
			}
		}
	}

	class Adapter : ListAdapter<MainViewModel.ScanData, Holder>(
			MainViewModel.SCAN_DATA_DIFF) {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
			val pad = (parent.context.resources.displayMetrics.density * 16).toInt()
			return Holder(TextView(parent.context).apply { setPadding(pad, pad, pad, pad) })
		}

		override fun onBindViewHolder(holder: Holder, position: Int) {
			(holder.itemView as TextView).text = getItem(position).text
		}
	}

	class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		init {
			itemView.setOnClickListener {

			}
		}
	}
}
