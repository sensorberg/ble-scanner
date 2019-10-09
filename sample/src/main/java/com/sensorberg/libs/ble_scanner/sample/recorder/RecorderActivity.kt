package com.sensorberg.libs.ble_scanner.sample.recorder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sensorberg.libs.ble_scanner.sample.R

class RecorderActivity : AppCompatActivity() {

	private var dialog: AlertDialog? = null
	private lateinit var address: String

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		address = intent.getStringExtra("address")!!

		setContentView(R.layout.activity_recorder)
		val toolbar: Toolbar = findViewById(R.id.toolbar)
		val recycler: RecyclerView = findViewById(R.id.recycler)
		recycler.layoutManager = LinearLayoutManager(this)

		toolbar.title = address

		val model = ViewModelProviders.of(this)
				.get(RecorderViewModel::class.java)

		// TODO setup adapter and observe on data

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

	companion object {
		fun start(context: Context, address: String) {
			val i = Intent(context, RecorderActivity::class.java)
			i.putExtra("address", address)
			context.startActivity(i)
		}
	}
}