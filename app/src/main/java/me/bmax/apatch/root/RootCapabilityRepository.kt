package me.bmax.apatch.root

import android.os.SystemClock
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object RootCapabilityRepository {
    private const val FreshTtlMs = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val sessionIds = AtomicLong(0L)
    private val _snapshot = MutableStateFlow(RootCapabilitySnapshot())

    val snapshot: StateFlow<RootCapabilitySnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var activeSessionId: Long = 0L

    @Volatile
    private var latestInitialization = RootInitializationSnapshot()

    private val kernelPatchObserver = Observer<APApplication.State> {
        handleCoreStateChanged()
    }
    private val androidPatchObserver = Observer<APApplication.State> {
        handleCoreStateChanged()
    }
    private val initializationObserver = Observer<RootInitializationSnapshot> { initialization ->
        latestInitialization = initialization
        when (initialization.phase) {
            RootCheckPhase.NOT_STARTED -> Unit
            RootCheckPhase.CHECKING -> {
                activeSessionId = initialization.sessionId
                _snapshot.value = RootCapabilityReducer.checking(
                    previous = _snapshot.value,
                    sessionId = initialization.sessionId,
                )
            }

            RootCheckPhase.READY -> applyInitialization(initialization)
            RootCheckPhase.FAILED -> {
                activeSessionId = 0L
                _snapshot.value = RootCapabilityReducer.fail(
                    previous = _snapshot.value,
                    error = initialization.error ?: RootCheckError.UNEXPECTED,
                    sessionId = initialization.sessionId,
                    checkedAt = initialization.completedAt ?: SystemClock.elapsedRealtime(),
                )
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        APApplication.kpStateLiveData.observeForever(kernelPatchObserver)
        APApplication.apStateLiveData.observeForever(androidPatchObserver)
        APApplication.rootInitializationLiveData.observeForever(initializationObserver)
    }

    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val current = _snapshot.value
        if (current.phase == RootCheckPhase.CHECKING) {
            return
        }
        if (!force && current.checkedAt != null && now - current.checkedAt < FreshTtlMs) {
            return
        }

        val sessionId = sessionIds.incrementAndGet()
        activeSessionId = sessionId
        _snapshot.value = RootCapabilityReducer.checking(
            previous = current,
            sessionId = sessionId,
        )

        scope.launch {
            val result = runCatching { AndroidRootCapabilityProbe.probe() }
            if (activeSessionId != sessionId) {
                return@launch
            }

            val checkedAt = SystemClock.elapsedRealtime()
            _snapshot.update { previous ->
                result.fold(
                    onSuccess = { probeResult ->
                        RootCapabilityReducer.applyProbe(
                            previous = previous,
                            result = probeResult,
                            sessionId = sessionId,
                            checkedAt = checkedAt,
                        )
                    },
                    onFailure = {
                        RootCapabilityReducer.fail(
                            previous = previous,
                            error = RootCheckError.UNEXPECTED,
                            sessionId = sessionId,
                            checkedAt = checkedAt,
                        )
                    },
                )
            }
            activeSessionId = 0L
        }
    }

    private fun applyInitialization(initialization: RootInitializationSnapshot) {
        activeSessionId = 0L
        val result = RootCapabilityProjection.fromInitialization(
            initialization = initialization,
            kernelPatchState = APApplication.kpStateLiveData.value,
            androidPatchState = APApplication.apStateLiveData.value,
        )
        _snapshot.update { previous ->
            RootCapabilityReducer.applyProbe(
                previous = previous,
                result = result,
                sessionId = initialization.sessionId,
                checkedAt = initialization.completedAt ?: SystemClock.elapsedRealtime(),
            )
        }
    }

    private fun handleCoreStateChanged() {
        activeSessionId = 0L
        val initialization = latestInitialization
        if (initialization.phase != RootCheckPhase.READY) {
            return
        }
        applyInitialization(initialization)
    }
}
