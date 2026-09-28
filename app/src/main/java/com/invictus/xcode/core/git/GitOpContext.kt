package com.invictus.xcode.core.git

import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Optional label callers can attach to a coroutine (`launch(io + GitTrigger("resume"))`) so the
 * git log can say *why* an operation ran (screen-open / resume / watcher / user / after-op).
 * Ops launched without one are logged as "user".
 */
class GitTrigger(val label: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GitTrigger>
}

/** Per-op accumulator [GitSession.ioOp] installs so lock-wait time can be reported separately from run time. */
internal class GitOpStats : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GitOpStats>

    private val waitNs = AtomicLong(0)

    fun addWait(ns: Long) {
        waitNs.addAndGet(ns)
    }

    fun totalWaitNs(): Long = waitNs.get()
}
