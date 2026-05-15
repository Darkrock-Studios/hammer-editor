package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.Path

/**
 * Renames scene files from the legacy `-`-delimited format (e.g. `1-Chapter Name-42.md`)
 * to the new `~`-delimited format (`1~Chapter Name~42.md`). The delimiter swap was needed
 * to free up `-` for use inside user-typed scene names. Walks the project's scenes
 * directory recursively and the `.archived` directory; the buffer directory only contains
 * id-based filenames so it's left alone.
 */
class Migration1_2(
	private val fileSystem: FileSystem,
) : Migration {
	override val toVersion: Int = 2

	override fun migrate(projectDef: ProjectDef) {
		Napier.i("Begin Migration1_2 for '${projectDef.name}'...")

		val sceneDir = SceneDatasource.getSceneDirectory(projectDef, fileSystem).toOkioPath()
		if (!fileSystem.exists(sceneDir)) {
			Napier.i("No scenes directory for '${projectDef.name}', skipping Migration1_2.")
			return
		}

		var renamed = 0
		// Rename deepest paths first so renaming a parent dir doesn't invalidate
		// pending child paths.
		fileSystem.listRecursively(sceneDir)
			.filter { !it.isInBufferDirectory() }
			.toList()
			.sortedByDescending { it.segments.size }
			.forEach { path ->
				val newName = nextName(path.name) ?: return@forEach
				val target = path.parent?.div(newName) ?: return@forEach
				if (fileSystem.exists(target)) {
					Napier.w("Migration1_2: target already exists, skipping rename of $path")
					return@forEach
				}
				fileSystem.atomicMove(path, target)
				renamed++
			}

		Napier.i("Migration1_2 for '${projectDef.name}' complete — renamed $renamed file(s).")
	}

	/**
	 * Returns the new filename for [oldName] if it matches the legacy active or archived
	 * scene pattern, or null if it doesn't match (e.g. directory name, buffer file, or
	 * already-migrated file). The trailing `.md`/`.temp` suffix is preserved verbatim.
	 */
	private fun nextName(oldName: String): String? {
		SceneDatasource.LEGACY_SCENE_FILENAME_PATTERN.matchEntire(oldName)?.let { m ->
			val (order, name, id) = Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
			val suffix = oldName.substringAfter("$order-$name-$id")
			return "$order~$name~$id$suffix"
		}
		SceneDatasource.LEGACY_ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(oldName)?.let { m ->
			val name = m.groupValues[1]
			val id = m.groupValues[2]
			val suffix = oldName.substringAfter("$name-$id")
			return "$name~$id$suffix"
		}
		return null
	}

	private fun Path.isInBufferDirectory(): Boolean {
		return toString().contains("${Path.DIRECTORY_SEPARATOR}${SceneDatasource.BUFFER_DIRECTORY}${Path.DIRECTORY_SEPARATOR}") ||
			parent?.name == SceneDatasource.BUFFER_DIRECTORY
	}
}
