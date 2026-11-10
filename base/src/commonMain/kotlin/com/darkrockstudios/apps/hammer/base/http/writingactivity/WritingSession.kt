package com.darkrockstudios.apps.hammer.base.http.writingactivity

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A single block of writing activity recorded on one device. Identity is the
 * composite of the owning device id (the file this lives in) and [startedAt].
 * Sessions extend in place while the user keeps writing on the same device,
 * then seal once a calendar-day rollover or a long inactivity gap closes them
 * for good. A sealed session is never edited again.
 *
 * Same shape on disk and over the wire — there's nothing the client knows
 * about a session that the server shouldn't see, or vice versa.
 */
@Serializable
data class WritingSession(
	val startedAt: Instant,
	val endedAt: Instant,
	val wordsWritten: Int,
	val sealed: Boolean = false,
)

/**
 * One device's contribution to a project's writing activity, by one author. Stored on the
 * client under `{project}/scenes/.activity/` and on the server as an opaque blob keyed by
 * `(project, author, deviceId)`. The [deviceLabel] is a friendly name written by the owning
 * device; other devices treat the file as read-only.
 *
 * [authorId] and [authorName] are stamped by the server, which ignores what a client sends.
 * Null means the project's owner: legacy data, or a slot not yet round-tripped.
 */
@Serializable
data class DeviceLog(
	val deviceLabel: String,
	val sessions: List<WritingSession> = emptyList(),
	val authorId: Long? = null,
	val authorName: String? = null,
)

/** A device's log; two accounts on one install share a [deviceId]. */
@Serializable
data class WritingActivitySlot(
	val deviceId: String,
	val log: DeviceLog,
)

/** Wire envelope returned by `GET /api/project/{userId}/{projectId}/writing_activity`. */
@Serializable
data class WritingActivityResponse(
	val slots: List<WritingActivitySlot> = emptyList(),
)
