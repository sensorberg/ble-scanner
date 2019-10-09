package com.sensorberg.libs.ble.scanner

import no.nordicsemi.android.support.v18.scanner.ScanResult

interface ScanResultCallback {
    fun onScanResult(scanResult: ScanResult)
}