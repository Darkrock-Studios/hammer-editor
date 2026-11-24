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

	// Built on first use so close() during DI shutdown never resolves the dispatcher. The lock
	// makes "closed" and "built" mutually exclusive, so nothing is built after close().
	private val lock = Any()
	private var closed = false
	private var selectOne: SharedTimedCall<Unit>? = null

	private fun selectOne(): SharedTimedCall<Unit>? = synchronized(lock) {
		if (closed) return null
		selectOne ?: SharedTimedCall(ioDispatcher) {
			database.driver.executeQuery(
				identifier = null,
				sql = "SELECT 1",
				mapper = { cursor -> QueryResult.Value(cursor.next().value) },
				parameters = 0,
				binders = null,
			)
			Unit
		}.also { selectOne = it }
	}

	enum class PingResult { OK, TIMED_OUT, CLOSED }

	/** Runs `SELECT 1` within [timeout]; throws if the query itself failed. */
	open suspend fun ping(timeout: Duration): PingResult {
		val call = selectOne() ?: return PingResult.CLOSED
		return try {
			call.get(timeout)
			PingResult.OK
		} catch (_: SharedTimedCall.UnavailableException) {
			if (call.isClosed) PingResult.CLOSED else PingResult.TIMED_OUT
		}
	}

	/** Abandons any ping still in flight; called when the DI container shuts down. */
	fun close() {
		synchronized(lock) {
			closed = true
			selectOne?.close()
		}
	}
}
