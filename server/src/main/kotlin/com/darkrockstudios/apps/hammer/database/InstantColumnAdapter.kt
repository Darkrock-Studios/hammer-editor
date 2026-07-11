package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.ColumnAdapter
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Adapts the JDBC Postgres `TIMESTAMPTZ` column type (`OffsetDateTime`) to
 * `kotlin.time.Instant` so DAOs and downstream code can stay in the
 * kotlinx-datetime / kotlin.time world.
 */
internal object InstantColumnAdapter : ColumnAdapter<Instant, OffsetDateTime> {
	override fun decode(databaseValue: OffsetDateTime): Instant =
		databaseValue.toInstant().toKotlinInstant()

	override fun encode(value: Instant): OffsetDateTime =
		// `Instant.MIN`/`MAX` reach further than an `OffsetDateTime`'s `LocalDate` can hold, so an
		// out-of-range value (e.g. a garbage client-supplied sync time) would throw DateTimeException.
		// The distant-past/future sentinels are the only meaningful values out here and both round-trip.
		OffsetDateTime.ofInstant(
			value.coerceIn(Instant.DISTANT_PAST, Instant.DISTANT_FUTURE).toJavaInstant(),
			ZoneOffset.UTC,
		)
}
