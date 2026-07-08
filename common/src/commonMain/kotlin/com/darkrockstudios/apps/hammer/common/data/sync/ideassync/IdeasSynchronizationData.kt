package com.darkrockstudios.apps.hammer.common.data.sync.ideassync

import com.darkrockstudios.apps.hammer.base.IdeaId
import kotlinx.serialization.Serializable

/**
 * Sidecar sync bookkeeping for story ideas, stored at `.ideas/sync.json`. All of it is
 * optional-by-design: losing this file degrades to baseline-less uploads (with baseline backfill
 * on the next sync) and loses not-yet-synced deletions — annoying but never content loss.
 *
 * [baselines] is each idea's conflict baseline: the hash last agreed with the server, locked at
 * sync time and never re-derived from current content (re-deriving manufactures phantom
 * conflicts — same reasoning as the entity journal's `syncedHashes`).
 *
 * [pendingDeletes] is a transient outbox, not a tombstone set: deletes recorded here await
 * propagation on the next sync and are erased on server ack. The server keeps the permanent
 * tombstones.
 */
@Serializable
data class IdeasSynchronizationData(
	val baselines: Map<IdeaId, String> = emptyMap(),
	val pendingDeletes: Set<IdeaId> = emptySet(),
)
