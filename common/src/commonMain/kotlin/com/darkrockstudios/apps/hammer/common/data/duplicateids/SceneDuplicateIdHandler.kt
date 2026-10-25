package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem
import okio.Path

/**
 * Active scenes, groups and archived scenes share one id space. Active copies come first, in name
 * order, then archived ones: the order scene lookups by id use.
 */
internal class SceneDuplicateIdHandler(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
) : DuplicateIdHandler(projectDef, fileSystem) {
	override val kind = DuplicateIdKind.Scene

	private val sceneDir get() = projectDir / SceneDatasource.SCENE_DIRECTORY
	private val archivedDir get() = sceneDir / SceneDatasource.ARCHIVED_DIRECTORY

	override fun findDuplicates(): List<DuplicateIdSet> {
		val active = listRecursivelyOrEmpty(sceneDir)
			.filter { path -> path.relativeTo(sceneDir).segments.none { it.startsWith(".") } }
			.mapNotNull { path -> parseActive(path)?.let { path to it } }
			.sortedBy { (path, _) -> path.name }
			.toList()

		val archived = listOrEmpty(archivedDir)
			.mapNotNull { path -> parseArchived(path)?.let { path to it } }
			.sortedBy { (path, _) -> path.name }

		return (active + archived).toDuplicateSets({ (_, name) -> name.id }) { (path, name) ->
			when {
				name.archived -> copyOf(path, name.displayName, readBytes(path), archived = true)
				name.isGroup -> copyOf(path, name.displayName, bytes = null)
				else -> copyOf(path, name.displayName, readBytes(path))
			}
		}
	}

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath {
		val path = copy.path.toOkioPath()
		return if (copy.archived) {
			val name = parseArchived(path) ?: error("Not an archived scene: $path")
			archivedDir / SceneDatasource.buildArchivedSceneFileName(name.displayName, id)
		} else {
			val name = parseActive(path) ?: error("Not a scene: $path")
			val extension = if (copy.isGroup) "" else SceneDatasource.SCENE_FILENAME_EXTENSION
			path.parent!! / "${name.order}~${ProjectsRepository.encodeForFilename(name.displayName)}~$id$extension"
		}.toHPath()
	}

	override fun claim(copy: DuplicateCopy, owner: DuplicateCopy, oldId: Int, newId: Int): Set<Int> {
		val target = canonicalPath(copy, newId)
		move(copy.path, target)
		// A re-id'd group's children now sit under a different parent id.
		return if (copy.isGroup && newId != oldId) {
			sceneIdsWithin(target.toOkioPath())
		} else {
			emptySet()
		}
	}

	/**
	 * A discarded group's children are kept: they move into the owner when it is an active group,
	 * otherwise up into the discarded group's own parent.
	 */
	override fun discard(copy: DuplicateCopy, owner: DuplicateCopy): Discarded {
		val path = copy.path.toOkioPath()
		if (!copy.isGroup) {
			fileSystem.delete(path)
			return Discarded()
		}

		// A group-named file holds no scenes.
		if (!fileSystem.metadata(path).isDirectory) {
			fileSystem.delete(path)
			return Discarded()
		}

		val ownerPath = owner.path.toOkioPath()
		val intoOwner = owner.isGroup && !owner.archived && !ownerPath.isDescendantOf(path) &&
			fileSystem.metadata(ownerPath).isDirectory
		val targetDir = if (intoOwner) ownerPath else path.parent!!
		var nextOrder = fileSystem.list(targetDir).count { parseActive(it) != null }
		val moved = mutableMapOf<HPath, HPath>()
		val dirty = mutableSetOf<Int>()

		val children = fileSystem.list(path).sortedBy { it.name }
		for (child in children) {
			val name = parseActive(child)
			val target = if (name != null) {
				val extension = if (name.isGroup) "" else SceneDatasource.SCENE_FILENAME_EXTENSION
				val encodedName = ProjectsRepository.encodeForFilename(name.displayName)
				generateSequence(nextOrder) { it + 1 }
					.map { order -> order to targetDir / "$order~$encodedName~${name.id}$extension" }
					.first { (_, candidate) -> !fileSystem.exists(candidate) }
					.let { (order, candidate) -> nextOrder = order + 1; candidate }
			} else {
				targetDir / child.name
			}
			// Only a file Hammer doesn't read can collide; it goes with the discarded group.
			if (fileSystem.exists(target)) continue

			fileSystem.atomicMove(child, target)
			moved[child.toHPath()] = target.toHPath()
			if (name != null) dirty += name.id
			dirty += sceneIdsWithin(target)
		}

		fileSystem.deleteRecursively(path)
		return Discarded(markDirty = dirty, moved = moved)
	}

	private fun sceneIdsWithin(dir: Path): Set<Int> {
		if (!fileSystem.metadata(dir).isDirectory) return emptySet()
		return fileSystem.listRecursively(dir).mapNotNull { parseActive(it)?.id }.toSet()
	}

	// Like SceneDatasource, a name without the scene extension is a group.
	private data class SceneName(
		val order: String,
		val displayName: String,
		val id: Int,
		val isGroup: Boolean,
		val archived: Boolean,
	)

	private fun parseActive(path: Path): SceneName? {
		val match = SceneDatasource.SCENE_FILENAME_PATTERN.matchEntire(path.name)
			?: SceneDatasource.LEGACY_SCENE_FILENAME_PATTERN.matchEntire(path.name)
			?: return null
		val id = match.groupValues[3].toIntOrNull() ?: return null
		val isGroup = match.groupValues[4] != SceneDatasource.SCENE_FILENAME_EXTENSION
		return SceneName(match.groupValues[1], ProjectsRepository.decodeFromFilename(match.groupValues[2]), id, isGroup, false)
	}

	private fun parseArchived(path: Path): SceneName? {
		val match = SceneDatasource.ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(path.name)
			?: SceneDatasource.LEGACY_ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(path.name)
			?: return null
		val id = match.groupValues[2].toIntOrNull() ?: return null
		return SceneName("", ProjectsRepository.decodeFromFilename(match.groupValues[1]), id, isGroup = false, archived = true)
	}
}
