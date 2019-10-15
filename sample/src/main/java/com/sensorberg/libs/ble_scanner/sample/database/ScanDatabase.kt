package com.sensorberg.libs.ble_scanner.sample.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [StoredScanBactch::class, StoredScanData::class], version = 1)
abstract class ScanDatabase : RoomDatabase() {
	abstract fun scanDao(): ScanDao
}