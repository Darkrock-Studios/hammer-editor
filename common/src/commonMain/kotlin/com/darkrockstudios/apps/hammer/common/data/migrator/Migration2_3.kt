package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.Path

/**
 * Renames encyclopedia entry files (`type-id-name.toml`), their images (`type-id-image.ext`), and
 * scene draft files (`sceneId-draftId-name-timestamp.md`) from the legacy `-`-delimited format to
 * the `~`-delimited one. The delimiter swap frees `-` (and the rest of the widened name character
 * set) for use inside user-typed entry and draft names, matching what [Migration1_2] did for
 * scenes.
 */
class Migration2_3(
	private val fileSystem: FileSystem,
) : Migration {
	override val toVersion: Int = 3

	override fun migrate(projectDef: ProjectDef) {
		Napier.i("Begin Migration2_3 for '${projectDef.name}'...")

		val renamed = migrateEncyclopedia(projectDef) + migrateDrafts(projectDef)

		Napier.i("Migration2_3 for '${projectDef.name}' complete, renamed $renamed file(s).")
	}

	private fun migrateEncyclopedia(projectDef: ProjectDef): Int {
		val projectDir = projectDef.path.toOkioPath()
		val encyclopediaDir = projectDir / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY
		if (!fileSystem.exists(encyclopediaDir)) {
			Napier.i("No encyclopedia directory for '${projectDef.name}', skipping.")
			return 0
		}

		return renameAll(encyclopediaDir) { name -> nextEntryName(name) }
	}

	private fun migrateDrafts(projectDef: ProjectDef): Int {
		val sceneDir = SceneDatasource.getSceneDirectory(projectDef, fileSystem).toOkioPath()
		val draftsDir = sceneDir / SceneDraftsDatasource.DRAFTS_DIR
		if (!fileSystem.exists(draftsDir)) {
			Napier.i("No drafts directory for '${projectDef.name}', skipping.")
			return 0
		}

		return renameAll(draftsDir) { name -> nextDraftName(name) }
	}

	private fun renameAll(root: Path, nextName: (String) -> String?): Int {
		var renamed = 0
		// Only files are renamed here, but walk deepest-first anyway so the traversal stays
		// correct if a future format nests names inside directory names.
		fileSystem.listRecursively(root)
			.toList()
			.sortedByDescending { it.segments.size }
			.forEach { path ->
				val newName = nextName(path.name) ?: return@forEach
				val target = path.parent?.div(newName) ?: return@forEach
				if (fileSystem.exists(target)) {
					Napier.w("Migration2_3: target already exists, skipping rename of $path")
					return@forEach
				}
				fileSystem.atomicMove(path, target)
				renamed++
			}
		return renamed
	}

	/**
	 * Returns the `~`-delimited name for [oldName] if it matches a legacy encyclopedia entry or
	 * entry image filename, or null if it doesn't match (e.g. a type directory, or a file already
	 * in the new format).
	 */
	private fun nextEntryName(oldName: String): String? {
		EncyclopediaDatasource.LEGACY_ENTRY_FILENAME_PATTERN.matchEntire(oldName)?.let { m ->
			val (type, id) = Pair(m.groupValues[1], m.groupValues[2])
			val name = canonicalName(m.groupValues[3]) ?: return null
			return "$type~$id~$name.toml"
		}
		EncyclopediaDatasource.LEGACY_ENTRY_IMAGE_FILENAME_PATTERN.matchEntire(oldName)?.let { m ->
			val (type, id, extension) = Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
			return "$type~$id~image.$extension"
		}
		return null
	}

	/**
	 * Returns the `~`-delimited name for [oldName] if it matches the legacy draft filename, or
	 * null if it doesn't match (e.g. a per-scene directory, or an already-migrated file).
	 */
	private fun nextDraftName(oldName: String): String? {
		val m = SceneDraftsDatasource.LEGACY_DRAFT_FILENAME_PATTERN.matchEntire(oldName)
			?: return null
		val sceneId = m.groupValues[1]
		val draftId = m.groupValues[2]
		val name = canonicalName(m.groupValues[3]) ?: return null
		val timestamp = m.groupValues[4]
		return "$sceneId~$draftId~$name~$timestamp.md"
	}

	/**
	 * The name exactly as the datasources would write it today. Both encode the name, which strips
	 * a trailing `.`/space that the old, narrower rules let through; without this the migrated file
	 * would never match the path rebuilt from its own parsed name. Null when nothing legal is left,
	 * so the file is skipped rather than renamed to an unparseable one.
	 */
	private fun canonicalName(rawName: String): String? =
		ProjectsRepository.encodeForFilename(rawName).ifBlank { null }
}
