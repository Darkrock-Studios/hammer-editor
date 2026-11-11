package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.writingactivity.SlotAuthor
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.data.writingactivity.mergeOwnSlotSessions
import com.darkrockstudios.apps.hammer.common.server.WritingActivityApi

/**
 * Syncs the project's per-device writing-activity logs. Inserted into the
 * project sync pipeline after entity transfer and before finalization.
 *
 * Flow (all merge logic on the client; server is dumb storage):
 *  1. GET every slot from the server.
 *  2. Wholesale-overwrite local copies of slots we don't own: other devices', and
 *     another account's on this device (two accounts on one install share a device id).
 *     An own file stamped with another account becomes that account's slot first.
 *  3. Merge the local copy of our own slot with the server's copy
 *     (deterministic union by `startedAt`), save the merged result both
 *     locally, with the author the server stamped, and back to the server.
 *  4. Invalidate the tracker's session cache so subsequent saves see the
 *     merged state instead of continuing from a possibly stale snapshot.
 *
 * Failure mode: if any step fails (server doesn't implement the endpoint,
 * network blip, etc.) we log a warning and pass through with the original
 * state. Writing activity is auxiliary — losing one sync of it shouldn't
 * fail the whole project sync.
 */
class WritingActivitySyncOperation(
	projectDef: ProjectDef,
	private val repository: WritingActivityRepository,
	private val tracker: WritingSessionTracker,
	private val api: WritingActivityApi,
	private val globalSettingsStore: GlobalSettingsStore,
	private val statisticsRepository: StatisticsRepository,
) : SyncOperation(projectDef) {

	// Auxiliary sync; must-not-crash boundary, failure logged and passed through.
	@Suppress("TooGenericExceptionCaught")
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend (success: Boolean) -> Unit,
	): CResult<SyncOperationState> {
		state as EntityTransferState

		try {
			val userId = globalSettingsStore.userIdOrThrow()
			val projectId = projectDef.id
			val ownDeviceId = repository.ownDeviceId()

			val getResult = api.getWritingActivity(userId, projectId)
			val response = getResult.getOrElse { error ->
				Logger.w("Writing activity sync: GET failed, skipping", error)
				onLog(syncLogW("Writing activity sync skipped: ${error.message}", projectDef))
				return CResult.success(state)
			}

			var changed = repository.claimOwnSlot(userId)
			val localOwnLog = repository.loadOwnLog()

			val isOwnSlot = { slot: WritingActivitySlot ->
				slot.deviceId == ownDeviceId && (slot.log.authorId == null || slot.log.authorId == userId)
			}
			val ownSlot = response.slots.firstOrNull(isOwnSlot)
			for (slot in response.slots) {
				if (!isOwnSlot(slot) && repository.replaceRemoteSlot(slot)) changed = true
			}

			val remoteOwnSessions = ownSlot?.log?.sessions ?: emptyList()
			val mergedSessions = mergeOwnSlotSessions(localOwnLog.sessions, remoteOwnSessions)
			val ownAuthor = ownSlot?.log?.authorId?.let { SlotAuthor(it, ownSlot.log.authorName) }
			repository.saveOwnLog(mergedSessions, ownAuthor)
			val authorChanged = ownAuthor != null &&
				(ownAuthor.id != localOwnLog.authorId || ownAuthor.name != localOwnLog.authorName)
			if (changed || authorChanged || mergedSessions != localOwnLog.sessions) {
				statisticsRepository.markDirty()
			}

			val payload = DeviceLog(
				deviceLabel = globalSettingsStore.deviceLabelOrDefault(),
				sessions = mergedSessions,
			)
			val postResult = api.uploadDeviceLog(
				userId = userId,
				projectId = projectId,
				deviceId = ownDeviceId,
				log = payload,
			)
			postResult.exceptionOrNull()?.let { error ->
				Logger.w("Writing activity sync: POST failed", error)
				onLog(syncLogW("Writing activity upload failed: ${error.message}", projectDef))
			}

			tracker.invalidateSessionCache()
			val slotCount = response.slots.count { !isOwnSlot(it) } + 1
			onLog(syncLogI("Writing activity synced ($slotCount slots)", projectDef))
		} catch (e: Exception) {
			Logger.w("Writing activity sync failed", e)
			onLog(syncLogW("Writing activity sync error: ${e.message}", projectDef))
		}

		return CResult.success(state)
	}
}
