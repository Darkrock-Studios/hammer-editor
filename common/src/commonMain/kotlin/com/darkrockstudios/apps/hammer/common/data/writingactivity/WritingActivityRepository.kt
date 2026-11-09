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
	 * Sync only: overwrites a slot this account does not own, another account's for this device
	 * included, and drops that device's pre-author file. Returns whether anything changed.
	 */
	suspend fun replaceRemoteSlot(slot: WritingActivitySlot): Boolean {
		val local = datasource.loadSlot(slot.deviceId, slot.log.authorId)
		datasource.saveSlot(slot)
		val droppedLegacy = slot.log.authorId != null && slot.deviceId != ownDeviceId() &&
			datasource.deleteDeviceLog(slot.deviceId)
		return slot.log != local || droppedLegacy
	}

	/**
	 * An own file stamped with another account becomes that account's synced slot, and [userId]'s
	 * synced copy of this device's slot is dropped. Returns whether a file changed.
	 */
	suspend fun claimOwnSlot(userId: Long): Boolean {
		val deviceId = ownDeviceId()
		val own = datasource.loadDeviceLog(deviceId)
		val author = own?.authorId
		val renamed = own != null && author != null && author != userId
		if (renamed) {
			datasource.saveSlot(WritingActivitySlot(deviceId, own))
			datasource.deleteDeviceLog(deviceId)
		}
		return datasource.deleteSlot(deviceId, userId) || renamed
	}
}
