package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path

/**
 * Reads and writes writing-activity slot files under `{project}/scenes/.activity/`. This
 * device's own slot is `{deviceId}.toml`; a slot synced from the server is
 * `{deviceId}.{authorId}.toml`, since two accounts on one install share a device id.
 * Sits alongside `.buffers` and `.archived` under the existing scene directory and follows
 * the same dotted-hidden-folder convention. The "only this device writes its own slot" rule
 * is enforced one layer up.
 */
class WritingActivityDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	fun getDirectory(): Path = activityDirectory(projectDef.path.toOkioPath())

	fun getDeviceLogPath(deviceId: String): Path = getDirectory() / "$deviceId$FILE_SUFFIX"

	suspend fun loadDeviceLog(deviceId: String): DeviceLog? = withContext(dispatcherIo) {
		val path = getDeviceLogPath(deviceId)
		if (!fileSystem.exists(path)) return@withContext null
		fileSystem.readTomlOrNull<DeviceLog>(path, toml) { e ->
			Napier.e("Failed to load writing activity log: $path", e)
		}
	}

	/** A synced slot's file; an unauthored one shares the own-slot name. */
	fun getSlotPath(deviceId: String, authorId: Long?): Path =
		if (authorId == null) getDeviceLogPath(deviceId) else getDirectory() / "$deviceId.$authorId$FILE_SUFFIX"

	suspend fun loadAllDeviceLogs(): List<WritingActivitySlot> = withContext(dispatcherIo) {
		val dir = getDirectory()
		if (!fileSystem.exists(dir)) return@withContext emptyList()
		fileSystem.list(dir)
			.sortedBy { it.name }
			.mapNotNull { path ->
				val deviceId = DEVICE_LOG_FILENAME_PATTERN.matchEntire(path.name)?.groupValues?.get(1)
					?: return@mapNotNull null
				val log = fileSystem.readTomlOrNull<DeviceLog>(path, toml) { e ->
					Napier.e("Failed to load writing activity log: $path", e)
				}
				log?.let { WritingActivitySlot(deviceId, it) }
			}
	}

	suspend fun saveDeviceLog(deviceId: String, log: DeviceLog): Unit = withContext(dispatcherIo) {
		writeDeviceLog(projectDef.path.toOkioPath(), deviceId, log, fileSystem, toml)
	}

	suspend fun loadSlot(deviceId: String, authorId: Long?): DeviceLog? = withContext(dispatcherIo) {
		val path = getSlotPath(deviceId, authorId)
		if (!fileSystem.exists(path)) return@withContext null
		fileSystem.readTomlOrNull<DeviceLog>(path, toml) { e ->
			Napier.e("Failed to load writing activity log: $path", e)
		}
	}

	/** False when there was no file to delete. */
	suspend fun deleteSlot(deviceId: String, authorId: Long): Boolean = withContext(dispatcherIo) {
		val path = getSlotPath(deviceId, authorId)
		val existed = fileSystem.exists(path)
		fileSystem.delete(path, mustExist = false)
		existed
	}

	suspend fun saveSlot(slot: WritingActivitySlot): Unit = withContext(dispatcherIo) {
		fileSystem.createDirectories(getDirectory())
		fileSystem.writeToml(getSlotPath(slot.deviceId, slot.log.authorId), toml, slot.log)
	}

	/** False when there was no file to delete. */
	suspend fun deleteDeviceLog(deviceId: String): Boolean = withContext(dispatcherIo) {
		val path = getDeviceLogPath(deviceId)
		val existed = fileSystem.exists(path)
		fileSystem.delete(path, mustExist = false)
		existed
	}

	companion object {
		const val ACTIVITY_DIRECTORY = ".activity"
		const val FILE_SUFFIX = ".toml"

		// Device ids are install UUIDs (or the example project's fixed id); synced slots add the author id.
		val DEVICE_LOG_FILENAME_PATTERN = Regex("""([A-Za-z0-9_-]+)(?:\.\d+)?\.toml""")
	}
}

/** The `.activity` directory under [projectRoot]; the single owner of the path convention. */
fun activityDirectory(projectRoot: Path): Path =
	projectRoot / SceneDatasource.SCENE_DIRECTORY / WritingActivityDatasource.ACTIVITY_DIRECTORY

/**
 * Blocking, scope-less write of a device's activity log under [projectRoot]; callers own
 * their threading. [WritingActivityDatasource.saveDeviceLog] delegates here; the
 * example-project installer uses it directly because the project isn't loaded.
 */
fun writeDeviceLog(
	projectRoot: Path,
	deviceId: String,
	log: DeviceLog,
	fileSystem: FileSystem,
	toml: Toml,
) {
	val dir = activityDirectory(projectRoot)
	fileSystem.createDirectories(dir)
	fileSystem.writeToml(dir / "$deviceId${WritingActivityDatasource.FILE_SUFFIX}", toml, log)
}
