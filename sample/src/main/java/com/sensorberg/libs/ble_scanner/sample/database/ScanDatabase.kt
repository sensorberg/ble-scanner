package com.sensorberg.libs.ble_scanner.sample.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ScanBatch::class, ScanData::class], version = 1)
abstract class ScanDatabase : RoomDatabase() {
	abstract fun scanDao(): ScanDao
}