package com.darkrockstudios.apps.hammer.health

import app.cash.sqldelight.db.QueryResult
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import kotlin.time.Duration

open class HealthCheckDao(
	private val database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	// The ping runs detached so a timeout can return while blocking JDBC (e.g. a pool
	// checkout waiting on Hikari's 30s connectionTimeout) is still in flight.
	private val pingScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

	/**
	 * Runs `SELECT 1`. Returns false if it didn't finish within [timeout];
	 * throws if the query itself failed.
	 */
	open suspend fun ping(timeout: Duration): Boolean {
		val query = pingScope.async {
			database.driver.executeQuery(
				identifier = null,
				sql = "SELECT 1",
				mapper = { cursor -> QueryResult.Value(cursor.next().value) },
				parameters = 0,
				binders = null,
			)
		}
		val finished = withTimeoutOrNull(timeout) { query.await() } != null
		if (!finished) query.cancel()
		return finished
	}
}
