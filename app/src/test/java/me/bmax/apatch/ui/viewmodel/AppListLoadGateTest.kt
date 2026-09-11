package me.bmax.apatch.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AppListLoadGateTest {

    @Test
    fun ensureLoaded_deduplicatesConcurrentInitialLoads() = runBlocking {
        val gate = AppListLoadGate()
        val loadCalls = AtomicInteger()
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        var hasData = false

        val jobs = List(8) {
            launch {
                gate.ensureLoaded(
                    hasData = { hasData },
                    load = {
                        loadCalls.incrementAndGet()
                        loadStarted.complete(Unit)
                        releaseLoad.await()
                        hasData = true
                    },
                )
            }
        }

        loadStarted.await()
        delay(50)
        releaseLoad.complete(Unit)
        jobs.joinAll()

        assertEquals(1, loadCalls.get())
    }

    @Test
    fun ensureLoaded_skipsLoadWhenDataAlreadyExists() = runBlocking {
        val gate = AppListLoadGate()
        val loadCalls = AtomicInteger()

        gate.ensureLoaded(
            hasData = { true },
            load = { loadCalls.incrementAndGet() },
        )

        assertEquals(0, loadCalls.get())
    }

    @Test
    fun reload_alwaysLoadsAndSerializesConcurrentRefreshes() = runBlocking {
        val gate = AppListLoadGate()
        val loadCalls = AtomicInteger()
        val runningLoads = AtomicInteger()
        val maxConcurrentLoads = AtomicInteger()

        val jobs = List(4) {
            launch {
                gate.reload {
                    loadCalls.incrementAndGet()
                    val running = runningLoads.incrementAndGet()
                    maxConcurrentLoads.updateAndGet { current -> maxOf(current, running) }
                    delay(25)
                    runningLoads.decrementAndGet()
                }
            }
        }
        jobs.joinAll()

        assertEquals(4, loadCalls.get())
        assertEquals(1, maxConcurrentLoads.get())
    }
}
