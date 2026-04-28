package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope

/**
 * Project-scoped facade over [WritingActivityDatasource]. Encodes the rule
 * that a device only ever writes its own slot — callers can read all
 * device logs but only persist this device's contribution.
 */
class WritingActivityRepository(
	private val datasource: WritingActivityDatasource,
	private val globalSettingsRepository: GlobalSettingsRepository,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	/** Returns this device's id, generating and persisting it on first call. */
	suspend fun ownDeviceId(): String = globalSettingsRepository.ensureInstallId()

	/**
	 * Loads this device's slot, returning an empty log labeled with the current
	 * device label when no slot file exists yet (e.g. first write on a new
	 * project, or fresh install).
	 */
	suspend fun loadOwnLog(): DeviceLog {
		val deviceId = ownDeviceId()
		return datasource.loadDeviceLog(deviceId)
			?: DeviceLog(deviceLabel = globalSettingsRepository.deviceLabelOrDefault())
	}

	/**
	 * Persists this device's sessions. The current device label (which may
	 * have been edited by the user) is written into the file alongside.
	 */
	suspend fun saveOwnLog(sessions: List<WritingSession>) {
		val deviceId = ownDeviceId()
		val log = DeviceLog(
			deviceLabel = globalSettingsRepository.deviceLabelOrDefault(),
			sessions = sessions,
		)
		datasource.saveDeviceLog(deviceId, log)
	}

	/** Reads every per-device log in this project's writing_activity folder. */
	suspend fun loadAllLogs(): Map<String, DeviceLog> = datasource.loadAllDeviceLogs()

	/**
	 * Sync-only entry point: replace another device's slot with the server's
	 * version. Wholesale overwrite is intentional — we never merge or
	 * otherwise edit slots we don't own. Should never be called for
	 * [ownDeviceId].
	 */
	suspend fun replaceForeignDeviceLog(deviceId: String, log: DeviceLog) {
		datasource.saveDeviceLog(deviceId, log)
	}
}
