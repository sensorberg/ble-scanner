package com.sensorberg.libs.ble.scanner.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.sensorberg.libs.ble.scanner.BleScanner
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LifeCycleBleScannerKtTest {

	private lateinit var bleScanner: BleScanner
	private lateinit var lifecycle: TestLifecycle

	@Before
	fun setUp() {
		lifecycle = TestLifecycle()
		bleScanner = mockk(relaxed = true)
		bleScanner.scanWithLifecycle(lifecycle)
	}

	@Test fun `when lifecycle STARTED, starts scan`() {
		lifecycle.state = Lifecycle.State.STARTED
		verify(exactly = 1) { bleScanner.start() }
	}

	@Test fun `when lifecycle RESUMED, starts scan`() {
		lifecycle.state = Lifecycle.State.RESUMED
		verify(exactly = 1) { bleScanner.start() }
	}

	@Test fun `when lifecycle STOPPED, stops scan`() {
		lifecycle.state = Lifecycle.State.RESUMED
		lifecycle.state = Lifecycle.State.CREATED
		verify(exactly = 1) { bleScanner.stopDelayed() }
	}

	@Test fun `when lifecycle DESTROYED, stops scan now`() {
		lifecycle.state = Lifecycle.State.RESUMED
		lifecycle.state = Lifecycle.State.DESTROYED
		verify(exactly = 1) { bleScanner.stopNow() }
	}

	@Test fun `when lifecycle DESTROYED, unregister observer`() {
		lifecycle.state = Lifecycle.State.RESUMED
		verify(exactly = 1) { bleScanner.start() }
		lifecycle.state = Lifecycle.State.DESTROYED
		lifecycle.state = Lifecycle.State.RESUMED
		verify(exactly = 1) { bleScanner.start() }
		lifecycle.state = Lifecycle.State.RESUMED
		verify(exactly = 1) { bleScanner.start() }
	}

	class TestLifecycle : LifecycleOwner {
		private val registry: LifecycleRegistry = LifecycleRegistry(this).apply {
			currentState = Lifecycle.State.INITIALIZED
		}

		override fun getLifecycle(): Lifecycle {
			return registry
		}

		var state: Lifecycle.State
			get() {
				return registry.currentState
			}
			set(value) {
				registry.currentState = value
			}
	}
}