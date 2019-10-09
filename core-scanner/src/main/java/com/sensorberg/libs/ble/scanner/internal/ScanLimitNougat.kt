package com.sensorberg.libs.ble.scanner.internal

import com.sensorberg.libs.time.Time

/**
 * This is to avoid being punished by Nougat for excessive scanning:
 * https://blog.classycode.com/undocumented-android-7-ble-behavior-changes-d1a9bd87d983
 * https://android-review.googlesource.com/c/platform/packages/apps/Bluetooth/+/215844/15/src/com/android/bluetooth/gatt/AppScanStats.java#144
 * As for now defined window is 30 second and less than 5 scans.
 */
class ScanLimitNougat(safety: Long = SAFETY) {

    private val limit = MAX_SCANS
    private val period = PERIOD + safety
    private val scans: MutableList<Long> = mutableListOf()
    private var debounce: Long = 0

    internal fun getPeriod(): Long {
        return period
    }

    /**
     * How long app has to delay next start to avoid being punished.
     * Refer to [Limit] documentation for individual params.
     */
    fun getStartDelay(): Limit {
        evict()
        val delay = if (scans.isEmpty()) {
            0L
        } else {
            period - (Time.getElapsedTime() - scans[0]) + 1L
        }
        return Limit(limit - scans.size, delay)
    }

    /**
     * Call right after starting BLE scan
     */
    fun onAfterStart() {
        evict()
        scans.add(Time.getElapsedTime())
    }

    private fun evict() {
        debounce {
            val it = scans.iterator()
            val now = Time.getElapsedTime()
            while (it.hasNext()) {
                val item = it.next()
                if (now - item > period) {
                    it.remove()
                } else {
                    break
                }
            }
        }
    }

    private fun debounce(run: () -> Unit) {
        val now = Time.getElapsedTime()
        if (now - debounce > 100) {
            debounce = now
            run.invoke()
        }
    }

    //Just for reference, how isScanningTooFrequently is defined in AOSP.
    /*
    private fun isScanningTooFrequently(): Boolean {
        if (scans.size < limit) {
            return false
        }
        return (System.currentTimeMillis() - scans[0]) < period
    }
    */

    companion object {
        const val SAFETY = 500L
        // Those should correspond to AOSP sources
        const val MAX_SCANS = 5
        const val PERIOD = 30_000L
    }
}

/**
 * @param startsLeft How many starts are left before hitting the limit.
 * This can be a negative number, indicating there was an excessive scanning
 * @param increaseIn How long in ms until [startsLeft] will be increased.
 */
data class Limit(val startsLeft: Int, val increaseIn: Long)