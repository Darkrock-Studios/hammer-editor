package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.QueryResult
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import kotlin.time.Duration

open class HealthCheckDao(
	private val database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	// The ping runs detached so a timeout can return while blocking JDBC (e.g. a pool
	// checkout waiting on Hikari's 30s connectionTimeout) is still in flight. Cancelling
	// can't interrupt that call, so a stuck ping is shared rather than repeated: at most
	// one thread and one connection are ever tied up by health checks.
	private val pingJob = SupervisorJob()
	private val pingScope by lazy { CoroutineScope(pingJob + ioDispatcher) }
	private val inFlightLock = Mutex()
	private var inFlight: Deferred<Unit>? = null

	/**
	 * Runs `SELECT 1`. Returns false if it didn't finish within [timeout];
	 * throws if the query itself failed.
	 */
	open suspend fun ping(timeout: Duration): Boolean {
		val query = inFlightLock.withLock {
			inFlight?.takeIf { it.isActive } ?: pingScope.async { selectOne() }.also { inFlight = it }
		}
		return withTimeoutOrNull(timeout) { query.await() } != null
	}

	/** Abandons any ping still in flight; called when the DI container shuts down. */
	fun close() {
		pingJob.cancel()
	}

	private fun selectOne() {
		database.driver.executeQuery(
			identifier = null,
			sql = "SELECT 1",
			mapper = { cursor -> QueryResult.Value(cursor.next().value) },
			parameters = 0,
			binders = null,
		)
	}
}
