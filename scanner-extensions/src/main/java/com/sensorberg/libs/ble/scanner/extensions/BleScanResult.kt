package com.sensorberg.libs.ble.scanner.extensions

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import no.nordicsemi.android.support.v18.scanner.ScanResult

@Parcelize data class BleScanResult(
	val scanResult: ScanResult,
	val averageRssi: Float) : Parcelable