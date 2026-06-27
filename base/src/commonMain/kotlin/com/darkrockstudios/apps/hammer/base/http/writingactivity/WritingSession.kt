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
 * One device's contribution to a project's writing activity. Stored on the
 * client at `{project}/scenes/.activity/{deviceId}.toml` and on the server
 * as an opaque blob keyed by `(project, deviceId)`. The [deviceLabel] is a
 * friendly name written by the owning device; other devices treat the file
 * as read-only.
 */
@Serializable
data class DeviceLog(
	val deviceLabel: String,
	val sessions: List<WritingSession> = emptyList(),
)

/**
 * Wire envelope returned by `GET /api/project/{userId}/{projectId}/writing_activity`.
 * Each device's log is keyed by its `deviceId` UUID.
 */
@Serializable
data class WritingActivityResponse(
	val devices: Map<String, DeviceLog> = emptyMap(),
)
