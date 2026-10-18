package com.darkrockstudios.apps.hammer.utilities

import kotlin.time.Instant

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** Floor an instant to the start (00:00) of its UTC day. */
fun Instant.truncateToUtcDay(): Instant =
	Instant.fromEpochMilliseconds(toEpochMilliseconds() / MILLIS_PER_DAY * MILLIS_PER_DAY)

/**
 * Coerce an instant into the range a Postgres `TIMESTAMPTZ` column can hold. Instants near
 * [Instant.DISTANT_PAST]..[Instant.DISTANT_FUTURE] round-trip; values beyond reach further than an
 * `OffsetDateTime`'s `LocalDate` can represent and would throw when encoded. Those extremes only ever
 * arise from garbage input, so clamping to the distant-past/future sentinels preserves ordering.
 */
fun Instant.coerceToStorableRange(): Instant =
	coerceIn(Instant.DISTANT_PAST, Instant.DISTANT_FUTURE)
