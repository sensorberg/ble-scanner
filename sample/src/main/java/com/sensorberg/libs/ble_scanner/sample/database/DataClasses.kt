package com.sensorberg.libs.ble_scanner.sample.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class StoredScanBactch(
	@PrimaryKey(autoGenerate = true) val id: Long,
	val title: String)

@Entity(tableName = "scans")
data class StoredScanData(
	@PrimaryKey val timestamp: Long,
	val batchId: Long,
	val rssi: Int)