package com.darkrockstudios.apps.hammer.common.data.sync.ideassync

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaHashItem
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeasStateHasher
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogW
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerIdeasApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_ideas_begin
import com.darkrockstudios.apps.hammer.sync_log_ideas_complete
import com.darkrockstudios.apps.hammer.sync_log_ideas_conflict
import com.darkrockstudios.apps.hammer.sync_log_ideas_conflict_resolved
import com.darkrockstudios.apps.hammer.sync_log_ideas_conflict_skipped
import com.darkrockstudios.apps.hammer.sync_log_ideas_delete_server_failed
import com.darkrockstudios.apps.hammer.sync_log_ideas_deleted_local
import com.darkrockstudios.apps.hammer.sync_log_ideas_deleted_server
import com.darkrockstudios.apps.hammer.sync_log_ideas_download_failed
import com.darkrockstudios.apps.hammer.sync_log_ideas_downloaded
import com.darkrockstudios.apps.hammer.sync_log_ideas_state_failed
import com.darkrockstudios.apps.hammer.sync_log_ideas_unchanged
import com.darkrockstudios.apps.hammer.sync_log_ideas_upload_failed
import com.darkrockstudios.apps.hammer.sync_log_ideas_uploaded
import io.github.aakira.napier.Napier
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.yield

/** A story-idea sync conflict awaiting the user's decision. */
data class IdeaConflict(
	val local: StoryIdea,
	val server: StoryIdea,
)

/**
 * Resolves an idea conflict: returns the idea to force-upload (either side, or a manual merge),
 * or null to leave the idea unsynced this session (it stays dirty and conflicts again next time).
 */
typealias IdeaConflictResolver = suspend (IdeaConflict) -> StoryIdea?

/**
 * The ideas phase of the account sync. Runs inside the projects sync session (same syncId):
 * fetches the server's idea state, reconciles deletions in both directions (tombstones win),
 * downloads missing/stale ideas, uploads dirty and server-unknown ones, and locks conflict
 * baselines in `.ideas/sync.json` as each idea reaches agreement.
 */
class ClientIdeasSynchronizer(
	private val ideasDatasource: IdeasDatasource,
	private val ideasSyncDatasource: IdeasSyncDatasource,
	private val ideasRepository: IdeasRepository,
	private val serverIdeasApi: ServerIdeasApi,
	private val strRes: StrRes,
) {
	suspend fun syncIdeas(
		syncId: String,
		onLog: OnSyncLog,
		serverIdeasStateHash: String? = null,
		resolveConflict: IdeaConflictResolver,
	): Boolean {
		if (serverIdeasStateHash != null && isInAgreement(serverIdeasStateHash)) {
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_unchanged)))
			return true
		}

		onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_begin)))

		val stateResult = serverIdeasApi.getSyncState(syncId)
		if (stateResult.isFailure) {
			onLog(
				syncAccLogE(
					strRes.get(
						Res.string.sync_log_ideas_state_failed,
						stateResult.exceptionOrNull() ?: "---",
					)
				)
			)
			return false
		}
		val serverState = stateResult.getOrThrow()

		var syncData = ideasSyncDatasource.load()
		var localIdeas = ideasDatasource.loadIdeas()
		var allSuccess = true

		// Server tombstones win: prune local copies, and drop any bookkeeping for them.
		val tombstoned = serverState.deletedIdeas
		localIdeas.filter { it.id in tombstoned }.forEach { idea ->
			ideasDatasource.deleteIdea(idea.id)
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_deleted_local, label(idea))))
		}
		localIdeas = localIdeas.filterNot { it.id in tombstoned }
		syncData = syncData.copy(
			baselines = syncData.baselines - tombstoned,
			pendingDeletes = syncData.pendingDeletes - tombstoned,
		)

		yield()

		// Every id we attempt to delete this session, captured before the outbox is drained on
		// ack — the server's idea list was fetched before these deletes landed, so these ids
		// must be excluded from the missing-ideas download pass below or they 404.
		val deletesInFlight = syncData.pendingDeletes

		// Push this client's pending deletes; erased from the outbox on server ack.
		for (id in deletesInFlight.toList()) {
			val result = serverIdeasApi.deleteIdea(id, syncId)
			if (result.isSuccess) {
				syncData = syncData.copy(
					pendingDeletes = syncData.pendingDeletes - id,
					baselines = syncData.baselines - id,
				)
				onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_deleted_server, id.id)))
			} else {
				allSuccess = false
				onLog(
					syncAccLogE(
						strRes.get(Res.string.sync_log_ideas_delete_server_failed, id.id)
					)
				)
			}
		}

		yield()

		val serverHashes = serverState.ideas.associate { it.id to it.hash }

		for (idea in localIdeas) {
			val localHash = IdeaHasher.hash(idea)
			val serverHash = serverHashes[idea.id]
			val baseline = syncData.baselines[idea.id]

			when {
				// In agreement — just make sure the baseline is locked.
				serverHash == localHash -> {
					if (baseline != localHash) {
						syncData = syncData.copy(baselines = syncData.baselines + (idea.id to localHash))
					}
				}

				// Server-unknown or locally dirty — upload. A stale baseline 409s into conflict
				// resolution inside uploadIdea.
				serverHash == null || localHash != baseline -> {
					val outcome = uploadIdea(idea, localHash, baseline, syncId, onLog, resolveConflict)
					syncData = outcome.applyTo(syncData)
					if (!outcome.success) allSuccess = false
				}

				// Local clean, server changed — take the server copy.
				else -> {
					if (!downloadIdea(idea.id, syncId, onLog)) {
						allSuccess = false
					} else {
						syncData = syncData.copy(
							baselines = syncData.baselines + (idea.id to serverHash),
						)
					}
				}
			}
			yield()
		}

		// Ideas the server has that we don't — but never ones we just deleted (the server list
		// predates our delete, so a just-acked delete would otherwise be re-downloaded and 404).
		val localIds = localIdeas.map { it.id }.toSet()
		val missing = serverState.ideas.filter {
			it.id !in localIds && it.id !in deletesInFlight
		}
		for (item in missing) {
			if (downloadIdea(item.id, syncId, onLog)) {
				syncData = syncData.copy(baselines = syncData.baselines + (item.id to item.hash))
			} else {
				allSuccess = false
			}
			yield()
		}

		// Writing the sidecar is also the "has synced ideas" marker for delete recording.
		ideasSyncDatasource.save(syncData)
		ideasRepository.loadIdeas()

		onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_complete)))
		return allSuccess
	}

	/**
	 * True when the ideas phase can be skipped outright: no pending local work (no outbox
	 * entries, every local idea hashes to its locked baseline, no local-only or missing ideas)
	 * and the server's live idea set hashes to the same state as those baselines. Any failure
	 * mode is conservative — the phase runs and reconciles the normal way.
	 */
	private suspend fun isInAgreement(serverIdeasStateHash: String): Boolean {
		val syncData = ideasSyncDatasource.load()
		if (syncData.pendingDeletes.isNotEmpty()) return false

		val localHashes = ideasDatasource.loadIdeas().associate { it.id to IdeaHasher.hash(it) }
		if (localHashes != syncData.baselines) return false

		val localStateHash = IdeasStateHasher.hash(
			localHashes.map { (id, hash) -> IdeaHashItem(id, hash) }
		)
		return localStateHash == serverIdeasStateHash
	}

	private data class UploadOutcome(
		val success: Boolean,
		val syncedIdea: StoryIdea? = null,
		val syncedHash: String? = null,
		val deletedByServer: IdeaId? = null,
	) {
		fun applyTo(data: IdeasSynchronizationData): IdeasSynchronizationData = when {
			syncedIdea != null && syncedHash != null ->
				data.copy(baselines = data.baselines + (syncedIdea.id to syncedHash))

			deletedByServer != null ->
				data.copy(baselines = data.baselines - deletedByServer)

			else -> data
		}
	}

	private suspend fun uploadIdea(
		idea: StoryIdea,
		localHash: String,
		baseline: String?,
		syncId: String,
		onLog: OnSyncLog,
		resolveConflict: IdeaConflictResolver,
	): UploadOutcome {
		val result = serverIdeasApi.uploadIdea(idea, baseline, syncId)
		if (result.isSuccess) {
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_uploaded, label(idea))))
			return UploadOutcome(success = true, syncedIdea = idea, syncedHash = localHash)
		}

		return when (val exception = result.exceptionOrNull()) {
			is IdeaConflictException -> {
				onLog(syncAccLogW(strRes.get(Res.string.sync_log_ideas_conflict, label(idea))))
				resolveIdeaConflict(idea, exception, syncId, onLog, resolveConflict)
			}

			is HttpFailureException -> {
				if (exception.statusCode == HttpStatusCode.Gone) {
					// Tombstoned server-side while we were offline: deletion wins over the edit.
					ideasDatasource.deleteIdea(idea.id)
					onLog(syncAccLogW(strRes.get(Res.string.sync_log_ideas_deleted_local, label(idea))))
					UploadOutcome(success = true, deletedByServer = idea.id)
				} else {
					onLog(
						syncAccLogE(
							strRes.get(Res.string.sync_log_ideas_upload_failed, label(idea), exception)
						)
					)
					UploadOutcome(success = false)
				}
			}

			else -> {
				onLog(
					syncAccLogE(
						strRes.get(Res.string.sync_log_ideas_upload_failed, label(idea), exception ?: "---")
					)
				)
				UploadOutcome(success = false)
			}
		}
	}

	private suspend fun resolveIdeaConflict(
		local: StoryIdea,
		conflict: IdeaConflictException,
		syncId: String,
		onLog: OnSyncLog,
		resolveConflict: IdeaConflictResolver,
	): UploadOutcome {
		val resolved = resolveConflict(IdeaConflict(local = local, server = conflict.conflict.server))
		if (resolved == null) {
			// Left unresolved on purpose: the idea stays dirty and will conflict again next sync.
			onLog(syncAccLogW(strRes.get(Res.string.sync_log_ideas_conflict_skipped, label(local))))
			return UploadOutcome(success = true)
		}

		// The conflict UI's local pane is freely editable, so a resolution can be blank or
		// over-limit — invalid ideas the normal editor rejects. Refuse to upload/persist one;
		// the idea stays dirty and re-conflicts next sync rather than corrupting local + server.
		if (ideasRepository.validateIdea(resolved.content, resolved.tags) != IdeaError.NONE) {
			onLog(syncAccLogW(strRes.get(Res.string.sync_log_ideas_conflict_skipped, label(local))))
			return UploadOutcome(success = true)
		}

		val resolvedHash = IdeaHasher.hash(resolved)
		val result = serverIdeasApi.uploadIdea(resolved, conflict.conflict.serverHash, syncId)
		return if (result.isSuccess) {
			ideasDatasource.updateIdea(resolved)
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_conflict_resolved, label(resolved))))
			UploadOutcome(success = true, syncedIdea = resolved, syncedHash = resolvedHash)
		} else {
			Napier.e("Failed to upload conflict resolution for idea ${local.id.id}")
			onLog(
				syncAccLogE(
					strRes.get(
						Res.string.sync_log_ideas_upload_failed,
						label(resolved),
						result.exceptionOrNull() ?: "---",
					)
				)
			)
			UploadOutcome(success = false)
		}
	}

	private suspend fun downloadIdea(id: IdeaId, syncId: String, onLog: OnSyncLog): Boolean {
		val result = serverIdeasApi.downloadIdea(id, syncId)
		return if (result.isSuccess) {
			val dto = result.getOrThrow()
			ideasDatasource.updateIdea(dto.idea)
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_ideas_downloaded, label(dto.idea))))
			true
		} else {
			onLog(
				syncAccLogE(
					strRes.get(
						Res.string.sync_log_ideas_download_failed,
						id.id,
						result.exceptionOrNull() ?: "---",
					)
				)
			)
			false
		}
	}

	private fun label(idea: StoryIdea): String =
		idea.title
			?: idea.content.lineSequence().firstOrNull { it.isNotBlank() }?.take(LABEL_LENGTH)
			?: idea.id.id

	companion object {
		private const val LABEL_LENGTH = 32
	}
}
