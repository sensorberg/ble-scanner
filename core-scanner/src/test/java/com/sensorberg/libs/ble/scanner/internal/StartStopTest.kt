package com.sensorberg.libs.ble.scanner.internal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class StartStopTest {

	@Test fun `only one start will ever be executed`() {
		val iterations = 100
		val waitForIt = CountDownLatch(iterations)
		val startStop = StartStop()
		val runnables = mutableListOf<StartRunnable>()
		val threads = mutableListOf<Thread>()

		repeat(iterations) {
			val r = StartRunnable(startStop, waitForIt)
			val t = Thread(r)
			runnables += r
			threads += t
			t.start()
		}

		waitForIt.await(3, TimeUnit.SECONDS)
		assertEquals(1, runnables.count { it.executed() })
	}

	private class StartRunnable(private val startStop: StartStop, private val counter: CountDownLatch) : Runnable {

		private val _startExecuted = AtomicBoolean(false)
		fun executed(): Boolean {
			return _startExecuted.get()
		}

		override fun run() {
			Thread.sleep(Random.nextLong(5))
			startStop.start {
				Thread.sleep(Random.nextLong(5))
				_startExecuted.set(true)
				Thread.sleep(Random.nextLong(3))
			}
			counter.countDown()
		}
	}
}