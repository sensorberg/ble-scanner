package com.sensorberg.libs.ble_scanner.sample.database

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface ScanDao {
	@Insert fun newBatch(batch: ScanBatch): Long
	@Insert fun newScan(scan: ScanData)
}