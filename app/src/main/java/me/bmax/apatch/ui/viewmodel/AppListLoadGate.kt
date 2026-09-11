package me.bmax.apatch.ui.viewmodel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AppListLoadGate {
    private val mutex = Mutex()

    suspend fun ensureLoaded(
        hasData: () -> Boolean,
        load: suspend () -> Unit,
    ) {
        mutex.withLock {
            if (!hasData()) {
                load()
            }
        }
    }

    suspend fun reload(load: suspend () -> Unit) {
        mutex.withLock {
            load()
        }
    }
}
