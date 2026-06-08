package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.data.writingactivity.mergeOwnSlotSessions
import com.darkrockstudios.apps.hammer.common.server.WritingActivityApi
import io.github.aakira.napier.Napier

/**
 * Syncs the project's per-device writing-activity logs. Inserted into the
 * project sync pipeline after entity transfer and before finalization.
 *
 * Flow (all merge logic on the client; server is dumb storage):
 *  1. GET every device's slot from the server.
 *  2. Wholesale-overwrite local copies of *other* devices' slots — we
 *     never edit slots we don't own.
 *  3. Merge the local copy of our own slot with the server's copy
 *     (deterministic union by `startedAt`), save the merged result both
 *     locally and back to the server.
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
) : SyncOperation(projectDef) {

	// Auxiliary sync; must-not-crash boundary, failure logged and passed through.
	@Suppress("TooGenericExceptionCaught")
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
	): CResult<SyncOperationState> {
		state as EntityTransferState

		try {
			val userId = globalSettingsStore.userIdOrThrow()
			val projectId = state.serverProjectId
			val ownDeviceId = repository.ownDeviceId()

			val getResult = api.getWritingActivity(userId, projectDef.name, projectId)
			val response = getResult.getOrElse { error ->
				Napier.w("Writing activity sync: GET failed, skipping", error)
				onLog(syncLogW("Writing activity sync skipped: ${error.message}", projectDef))
				return CResult.success(state)
			}

			for ((deviceId, log) in response.devices) {
				if (deviceId != ownDeviceId) {
					repository.replaceForeignDeviceLog(deviceId, log)
				}
			}

			val localOwnLog = repository.loadOwnLog()
			val remoteOwnSessions = response.devices[ownDeviceId]?.sessions ?: emptyList()
			val mergedSessions = mergeOwnSlotSessions(localOwnLog.sessions, remoteOwnSessions)
			repository.saveOwnLog(mergedSessions)

			val payload = DeviceLog(
				deviceLabel = globalSettingsStore.deviceLabelOrDefault(),
				sessions = mergedSessions,
			)
			val postResult = api.uploadDeviceLog(
				userId = userId,
				projectName = projectDef.name,
				projectId = projectId,
				deviceId = ownDeviceId,
				log = payload,
			)
			postResult.exceptionOrNull()?.let { error ->
				Napier.w("Writing activity sync: POST failed", error)
				onLog(syncLogW("Writing activity upload failed: ${error.message}", projectDef))
			}

			tracker.invalidateSessionCache()
			onLog(syncLogI("Writing activity synced (${response.devices.size + 1} devices)", projectDef))
		} catch (e: Exception) {
			Napier.w("Writing activity sync failed", e)
			onLog(syncLogW("Writing activity sync error: ${e.message}", projectDef))
		}

		return CResult.success(state)
	}
}
