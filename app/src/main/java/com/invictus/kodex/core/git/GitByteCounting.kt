package com.invictus.kodex.core.git

import org.eclipse.jgit.transport.HttpTransport
import org.eclipse.jgit.transport.http.HttpConnection
import org.eclipse.jgit.transport.http.HttpConnectionFactory
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory
import java.io.FilterInputStream
import java.io.InputStream
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports raw bytes read off the wire during a git network operation on the current thread.
 *
 * JGit's [org.eclipse.jgit.lib.ProgressMonitor] only ever reports an *object* count for
 * "Receiving objects" — never bytes — so there is no supported way to get a live MiB/speed
 * readout from the high-level API. The only hook JGit exposes for the raw HTTP bytes is a
 * *global, static* [HttpTransport.setConnectionFactory]. Since that factory is shared by the
 * whole JVM, a [ThreadLocal] is what lets each concurrent git operation (each running on its
 * own IO-dispatcher thread) see only its own byte counts.
 */
internal object GitByteTracker {
    private val current = ThreadLocal<((Long) -> Unit)?>()

    internal fun activeListener(): ((Long) -> Unit)? = current.get()

    /** Runs [block] on this thread with [onBytesRead] wired up to any HTTP reads it performs. */
    fun <T> track(onBytesRead: (totalBytes: Long) -> Unit, block: () -> T): T {
        current.set(onBytesRead)
        try {
            return block()
        } finally {
            current.remove()
        }
    }
}

private class CountingHttpConnection(
    private val delegate: HttpConnection,
) : HttpConnection by delegate {
    override fun getInputStream(): InputStream {
        val stream = delegate.inputStream
        val listener = GitByteTracker.activeListener() ?: return stream
        return object : FilterInputStream(stream) {
            private var total = 0L
            override fun read(): Int {
                val b = super.read()
                if (b >= 0) listener(++total)
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) {
                    total += n
                    listener(total)
                }
                return n
            }
        }
    }
}

private object CountingHttpConnectionFactory : HttpConnectionFactory {
    private val base = JDKHttpConnectionFactory()
    override fun create(url: URL): HttpConnection = CountingHttpConnection(base.create(url))
    override fun create(url: URL, proxy: Proxy?): HttpConnection = CountingHttpConnection(base.create(url, proxy))
}

private val installed = AtomicBoolean(false)

/** Installs [CountingHttpConnectionFactory] as JGit's global HTTP factory. Idempotent; call once at app startup. */
fun installGitByteCounting() {
    if (installed.compareAndSet(false, true)) {
        HttpTransport.setConnectionFactory(CountingHttpConnectionFactory)
    }
}
