package com.darkrockstudios.apps.hammer.database

import java.sql.SQLException

internal const val SQLSTATE_UNIQUE_VIOLATION = "23505"

// The cause chain is walked because the driver's violation can reach us wrapped by the pool.
internal fun SQLException.isUniqueViolation(): Boolean =
	generateSequence(this as Throwable) { it.cause }
		.any { it is SQLException && it.sqlState == SQLSTATE_UNIQUE_VIOLATION }
