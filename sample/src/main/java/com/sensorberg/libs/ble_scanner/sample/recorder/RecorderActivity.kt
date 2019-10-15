package com.sensorberg.libs.ble_scanner.sample.recorder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sensorberg.libs.ble_scanner.sample.R

class RecorderActivity : AppCompatActivity() {

	private var dialog: AlertDialog? = null
	private lateinit var address: String

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		address = intent.getStringExtra("address")!!

		setContentView(R.layout.activity_recorder)
		val recycler: RecyclerView = findViewById(R.id.recycler)
		val adapter = Adapter()
		recycler.layoutManager = LinearLayoutManager(this)
		recycler.adapter = adapter

		val model = ViewModelProviders.of(this)
				.get(RecorderViewModel::class.java)
		model.data.observe(this, Observer { adapter.submitList(it) })
		showStartRecordDialog()
	}

	override fun onDestroy() {
		dialog?.dismiss()
		dialog = null
		super.onDestroy()
	}

	private fun showStartRecordDialog() {
		val pad = (resources.displayMetrics.density * 16).toInt()
		val editText = EditText(this).apply {
			setPadding(pad, pad, pad, pad)
		}
		dialog = AlertDialog.Builder(this)
				.setView(editText)
				.setPositiveButton("REC") { dialogInterface, _ ->
					dialogInterface.dismiss()
					val text = editText.text.toString()
					if (text.isNotEmpty()) {
						val model = ViewModelProviders.of(this)
								.get(RecorderViewModel::class.java)
						model.record(text, address)
					}
				}
				.setNegativeButton("Cancel") { dialogInterface, _ ->
					dialogInterface.cancel()
				}
				.setCancelable(true)
				.show()
	}

	class Adapter : ListAdapter<RecorderViewModel.UiData, Holder>(
			RecorderViewModel.SCAN_DATA_DIFF) {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
			val pad = (parent.context.resources.displayMetrics.density * 16).toInt()
			return Holder(TextView(parent.context).apply { setPadding(pad, pad, pad, pad) })
		}

		override fun onBindViewHolder(holder: Holder, position: Int) {
			(holder.itemView as TextView).text = getItem(position).text
		}
	}

	class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

	companion object {
		fun start(context: Context, address: String) {
			val i = Intent(context, RecorderActivity::class.java)
			i.putExtra("address", address)
			context.startActivity(i)
		}
	}
}