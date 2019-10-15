package com.sensorberg.libs.ble_scanner.sample.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScanDao {
	@Insert fun newBatch(batch: StoredScanBactch): Long
	@Insert fun newScan(scan: StoredScanData)

	@Query("SELECT * from scans WHERE batchId = :batchId ORDER BY timestamp")
	fun getScans(batchId: Long): LiveData<List<StoredScanData>>

	@Query("SELECT * from batches")
	fun getBatchList(): List<StoredScanBactch>
}