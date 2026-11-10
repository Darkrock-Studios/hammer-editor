package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.QueryResult
import com.darkrockstudios.apps.hammer.utilities.SharedTimedCall
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import org.koin.core.component.KoinComponent
import kotlin.time.Duration

open class HealthCheckDao(
	private val database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	// Lazy so close() during DI shutdown never resolves the dispatcher.
	private val selectOne = lazy {
		SharedTimedCall(ioDispatcher) {
			database.driver.executeQuery(
				identifier = null,
				sql = "SELECT 1",
				mapper = { cursor -> QueryResult.Value(cursor.next().value) },
				parameters = 0,
				binders = null,
			)
		}
	}

	/**
	 * Runs `SELECT 1`. Returns false if it didn't finish within [timeout] (or health checks
	 * are shutting down); throws if the query itself failed.
	 */
	open suspend fun ping(timeout: Duration): Boolean =
		try {
			selectOne.value.get(timeout)
			true
		} catch (_: SharedTimedCall.UnavailableException) {
			false
		}

	/** Abandons any ping still in flight; called when the DI container shuts down. */
	fun close() {
		if (selectOne.isInitialized()) selectOne.value.close()
	}
}
