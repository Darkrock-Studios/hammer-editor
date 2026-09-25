package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import org.koin.core.scope.Scope

/** One plugin's view of one open project. */
class ProjectPluginContext internal constructor(
	val pluginId: String,
	val projectDef: ProjectDef,
	val projectScope: Scope,
	/** Cancelled when the project closes. */
	val coroutineScope: CoroutineScope,
	private val fileSystem: FileSystem,
) {
	/** `<project>/.plugins/<pluginId>/`, created on first call. Included in backups, never synced. */
	fun dataDirectory(): HPath = dataDirectory(projectDef, pluginId, fileSystem)

	companion object {
		const val PLUGINS_DIRECTORY = ".plugins"

		/** [dataDirectory] for a project that need not be open, such as from an operation. */
		fun dataDirectory(projectDef: ProjectDef, pluginId: String, fileSystem: FileSystem): HPath {
			val dir = projectDef.path.toOkioPath() / PLUGINS_DIRECTORY / pluginId
			fileSystem.createDirectories(dir)
			return dir.toHPath()
		}
	}
}
