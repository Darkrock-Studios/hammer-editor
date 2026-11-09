package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope

/** Who the server says wrote a slot. */
data class SlotAuthor(val id: Long, val name: String?)

/**
 * Project-scoped facade over [WritingActivityDatasource]. Encodes the rule
 * that a device only ever writes its own slot: callers can read all
 * slots but only persist this device's contribution.
 */
class WritingActivityRepository(
	private val datasource: WritingActivityDatasource,
	private val globalSettingsStore: GlobalSettingsStore,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	/** Returns this device's id, generating and persisting it on first call. */
	suspend fun ownDeviceId(): String = globalSettingsStore.ensureInstallId()

	/**
	 * Loads this device's slot, returning an empty log labeled with the current
	 * device label when no slot file exists yet (e.g. first write on a new
	 * project, or fresh install).
	 */
	suspend fun loadOwnLog(): DeviceLog {
		val deviceId = ownDeviceId()
		return datasource.loadDeviceLog(deviceId)
			?: DeviceLog(deviceLabel = globalSettingsStore.deviceLabelOrDefault())
	}

	/**
	 * Persists this device's sessions under the current device label (which may
	 * have been edited by the user). [author] replaces the stored author; null keeps it.
	 */
	suspend fun saveOwnLog(sessions: List<WritingSession>, author: SlotAuthor? = null) {
		val deviceId = ownDeviceId()
		val stored = if (author == null) datasource.loadDeviceLog(deviceId) else null
		val log = DeviceLog(
			deviceLabel = globalSettingsStore.deviceLabelOrDefault(),
			sessions = sessions,
			authorId = author?.id ?: stored?.authorId,
			authorName = author?.name ?: stored?.authorName,
		)
		datasource.saveDeviceLog(deviceId, log)
	}

	/** Every slot in this project's `.activity` folder, this device's own included. */
	suspend fun loadAllLogs(): List<WritingActivitySlot> = datasource.loadAllDeviceLogs()

	/**
	 * Sync-only entry point: replace a slot this account does not own with the server's
	 * version. Wholesale overwrite is intentional: we never merge or otherwise edit other
	 * devices' slots. The pre-author file of another device is dropped once its authored
	 * copy arrives. Another account's slot for this device was written here, so the local
	 * copy may hold sessions that account has not uploaded yet; it is merged instead.
	 */
	suspend fun replaceRemoteSlot(slot: WritingActivitySlot) {
		val authorId = slot.log.authorId
		val ownDeviceId = ownDeviceId()
		if (authorId != null && slot.deviceId == ownDeviceId) {
			val local = datasource.loadSlot(ownDeviceId, authorId)?.sessions.orEmpty()
			val merged = mergeOwnSlotSessions(local, slot.log.sessions)
			datasource.saveSlot(slot.copy(log = slot.log.copy(sessions = merged)))
			return
		}
		datasource.saveSlot(slot)
		if (authorId != null) datasource.deleteDeviceLog(slot.deviceId)
	}

	/**
	 * Moves this device's slot aside as [log]'s author's slot, so the signed-in account
	 * starts a fresh own log. For an own file written under another account on this install;
	 * [takeSetAsideSessions] gives it back when that account signs in again.
	 */
	suspend fun setAsideOwnLog(log: DeviceLog) {
		val deviceId = ownDeviceId()
		val authorId = log.authorId ?: return
		val earlier = datasource.loadSlot(deviceId, authorId)?.sessions.orEmpty()
		val merged = log.copy(sessions = mergeOwnSlotSessions(earlier, log.sessions))
		datasource.saveSlot(WritingActivitySlot(deviceId, merged))
		datasource.deleteDeviceLog(deviceId)
	}

	/** Removes and returns the sessions [authorId] left on this device under another account. */
	suspend fun takeSetAsideSessions(authorId: Long): List<WritingSession> {
		val deviceId = ownDeviceId()
		val log = datasource.loadSlot(deviceId, authorId) ?: return emptyList()
		datasource.deleteSlot(deviceId, authorId)
		return log.sessions
	}
}
