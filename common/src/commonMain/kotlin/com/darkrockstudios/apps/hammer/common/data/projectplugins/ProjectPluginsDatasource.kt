package com.darkrockstudios.apps.hammer.common.data.projectplugins

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Owns `project_plugins.toml`: the plugins each project turned off on this device, by project id.
 * Kept in the config directory, never in the project, so it is not synced.
 */
class ProjectPluginsDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) {
	/** Empty when the file is missing or unreadable: every plugin is then on. */
	fun load(): Map<ProjectId, Set<String>> {
		if (!fileSystem.exists(FILE_PATH)) return emptyMap()
		val stored = fileSystem.readTomlOrNull<StoredProjectPlugins>(FILE_PATH, toml) { e ->
			Napier.e("Failed to read $FILE_NAME", e)
		} ?: return emptyMap()
		return stored.projects.entries.associate { (id, project) -> ProjectId(id) to project.disabled }
	}

	fun store(disabled: Map<ProjectId, Set<String>>) {
		val projects = disabled.filterValues { it.isNotEmpty() }
			.entries.associate { (id, plugins) -> id.id to StoredProject(plugins) }
		fileSystem.createDirectories(FILE_PATH.parent!!)
		fileSystem.writeToml(FILE_PATH, toml, StoredProjectPlugins(projects))
	}

	@Serializable
	private data class StoredProjectPlugins(val projects: Map<String, StoredProject> = emptyMap())

	@Serializable
	private data class StoredProject(val disabled: Set<String> = emptySet())

	companion object {
		private const val FILE_NAME = "project_plugins.toml"
		val FILE_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}
