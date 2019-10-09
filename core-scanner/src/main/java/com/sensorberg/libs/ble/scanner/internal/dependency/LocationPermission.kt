package com.sensorberg.libs.ble.scanner.internal.dependency

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.sensorberg.libs.ble.scanner.TAG
import com.sensorberg.observable.ObservableData
import timber.log.Timber

class LocationPermission(context: Context) : ObservableData<Boolean>() {

	private val app = context.applicationContext as Application

	private val c = object : Application.ActivityLifecycleCallbacks {
		override fun onActivityResumed(activity: Activity?) {
			if (app.checkCallingOrSelfPermission
					(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				Timber.d("$TAG. Location permission is granted")
				value = true
				app.unregisterActivityLifecycleCallbacks(this)
			}
		}

		// ignore all other callbacks
		override fun onActivityPaused(activity: Activity?) {}

		override fun onActivityStarted(activity: Activity?) {}

		override fun onActivityDestroyed(activity: Activity?) {}

		override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}

		override fun onActivityStopped(activity: Activity?) {}

		override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {}

	}

	init {
		value = if (app.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			Timber.d("$TAG. Location permission is granted")
			true
		} else {
			Timber.d("$TAG. Location permission is denied")
			app.registerActivityLifecycleCallbacks(c)
			false
		}
	}
}