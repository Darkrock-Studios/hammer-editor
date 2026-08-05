package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.ColumnAdapter
import com.darkrockstudios.apps.hammer.utilities.coerceToStorableRange
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Adapts the JDBC Postgres `TIMESTAMPTZ` column type (`OffsetDateTime`) to
 * `kotlin.time.Instant` so DAOs and downstream code can stay in the
 * kotlin.time world.
 */
internal object InstantColumnAdapter : ColumnAdapter<Instant, OffsetDateTime> {
	private val logger = LoggerFactory.getLogger(InstantColumnAdapter::class.java)

	override fun decode(databaseValue: OffsetDateTime): Instant =
		databaseValue.toInstant().toKotlinInstant()

	override fun encode(value: Instant): OffsetDateTime {
		// Last line of defense: an out-of-range value reaching here would throw DateTimeException and
		// 500 the request. Callers should clamp (and log) at their boundary; this guarantees they can't
		// crash the write if one slips through.
		val clamped = value.coerceToStorableRange()
		if (clamped != value) {
			logger.warn("Clamped out-of-range instant before encoding: $value -> $clamped")
		}
		return OffsetDateTime.ofInstant(clamped.toJavaInstant(), ZoneOffset.UTC)
	}
}
