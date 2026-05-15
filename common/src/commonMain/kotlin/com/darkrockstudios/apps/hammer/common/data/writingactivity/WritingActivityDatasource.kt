package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
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
 * Reads and writes per-device writing-activity log files at
 * `{project}/scenes/.activity/{deviceId}.toml`. Sits alongside `.buffers`
 * and `.archived` under the existing scene directory and follows the same
 * dotted-hidden-folder convention. Stateless aside from the injected
 * file system; callers (the repository) decide which deviceId(s) to
 * interact with. The "only this device writes its own slot" rule is
 * enforced one layer up — this datasource will happily write any deviceId
 * if asked.
 */
class WritingActivityDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	fun getDirectory(): Path =
		projectDef.path.toOkioPath() / SceneDatasource.SCENE_DIRECTORY / ACTIVITY_DIRECTORY

	fun getDeviceLogPath(deviceId: String): Path = getDirectory() / "$deviceId$FILE_SUFFIX"

	suspend fun loadDeviceLog(deviceId: String): DeviceLog? = withContext(dispatcherIo) {
		val path = getDeviceLogPath(deviceId)
		if (!fileSystem.exists(path)) return@withContext null
		try {
			fileSystem.readToml(path, toml)
		} catch (e: Exception) {
			Napier.e("Failed to load writing activity log: $path", e)
			null
		}
	}

	suspend fun loadAllDeviceLogs(): Map<String, DeviceLog> = withContext(dispatcherIo) {
		val dir = getDirectory()
		if (!fileSystem.exists(dir)) return@withContext emptyMap()
		fileSystem.list(dir)
			.filter { it.name.endsWith(FILE_SUFFIX) }
			.mapNotNull { path ->
				val deviceId = path.name.removeSuffix(FILE_SUFFIX)
				val log: DeviceLog? = try {
					fileSystem.readToml(path, toml)
				} catch (e: Exception) {
					Napier.e("Failed to load writing activity log: $path", e)
					null
				}
				log?.let { deviceId to it }
			}.toMap()
	}

	suspend fun saveDeviceLog(deviceId: String, log: DeviceLog): Unit = withContext(dispatcherIo) {
		fileSystem.createDirectories(getDirectory())
		fileSystem.writeToml(getDeviceLogPath(deviceId), toml, log)
	}

	companion object {
		const val ACTIVITY_DIRECTORY = ".activity"
		const val FILE_SUFFIX = ".toml"
	}
}
