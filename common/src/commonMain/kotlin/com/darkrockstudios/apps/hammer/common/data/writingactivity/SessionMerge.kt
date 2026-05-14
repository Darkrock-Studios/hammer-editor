package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession

/**
 * Deterministic union of two session lists for the *same* device's slot.
 * Sessions are matched by [WritingSession.startedAt]; on collision we take
 * the one with the higher `wordsWritten` and later `endedAt`, plus seal if
 * either was sealed (sealing is one-way). The result is sorted by start time.
 *
 * This only runs for the device that owns the slot; cross-device "merging"
 * never happens because no device ever writes another device's file.
 */
fun mergeOwnSlotSessions(
	local: List<WritingSession>,
	remote: List<WritingSession>,
): List<WritingSession> {
	if (local.isEmpty()) return remote.sortedBy { it.startedAt }
	if (remote.isEmpty()) return local.sortedBy { it.startedAt }

	val byStart = HashMap<kotlin.time.Instant, WritingSession>(local.size + remote.size)
	for (session in local) byStart[session.startedAt] = session
	for (session in remote) {
		val existing = byStart[session.startedAt]
		byStart[session.startedAt] = if (existing == null) {
			session
		} else {
			existing.copy(
				endedAt = if (session.endedAt > existing.endedAt) session.endedAt else existing.endedAt,
				wordsWritten = if (session.wordsWritten > existing.wordsWritten) {
					session.wordsWritten
				} else {
					existing.wordsWritten
				},
				sealed = existing.sealed || session.sealed,
			)
		}
	}
	return byStart.values.sortedBy { it.startedAt }
}
