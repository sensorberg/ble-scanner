package com.sensorberg.libs.ble_scanner.sample.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ScanBatch(
	@PrimaryKey(autoGenerate = true) val id: Long,
	val title: String)

@Entity
data class ScanData(
	@PrimaryKey val timestamp: Long,
	val batchId: Long,
	val rssi: Int)