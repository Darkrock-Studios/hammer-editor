package com.darkrockstudios.apps.hammer.utilities

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Runs [block] detached on [context] and shares it while it is in flight, so callers can give
 * up after a timeout without each starting another copy of work that can't be interrupted
 * (blocking JDBC). However long the block hangs, it occupies at most one thread.
 */
class SharedTimedCall<T>(
	context: CoroutineContext,
	private val block: suspend () -> T,
) {
	private val job = SupervisorJob()
	private val scope = CoroutineScope(context + job)
	private val lock = Mutex()
	private var inFlight: Deferred<T>? = null

	/** Thrown when the call didn't finish within the timeout, or after [close]. */
	class UnavailableException(message: String) : Exception(message)

	/**
	 * The block's result, or its own exception if it failed. A failed or finished call is
	 * not reused; the next [get] runs the block again.
	 */
	suspend fun get(timeout: Duration): T {
		val call = lock.withLock {
			if (!job.isActive) throw UnavailableException("closed")
			inFlight?.takeIf { it.isActive } ?: scope.async { block() }.also { inFlight = it }
		}
		withTimeoutOrNull(timeout) { call.join() } ?: throw UnavailableException("timed out after $timeout")
		// join() returns normally however the call ended; tell a close() apart from a real failure.
		if (call.getCompletionExceptionOrNull() is CancellationException) throw UnavailableException("closed")
		return call.await()
	}

	/** Abandons the call in flight; later [get]s fail fast with [UnavailableException]. */
	fun close() {
		job.cancel()
	}
}
