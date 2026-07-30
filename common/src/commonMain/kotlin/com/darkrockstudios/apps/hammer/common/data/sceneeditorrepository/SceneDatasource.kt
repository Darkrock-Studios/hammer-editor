package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource.Companion.validateSceneFilename
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.IOException
import okio.Path

class SceneDatasource(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
) {

	private val scenePathIndex = ScenePathIndex(::scanScenePaths)

	private fun invalidateScenePathCache() = scenePathIndex.invalidate()

	private fun addScenePathToCache(scenePath: HPath) = scenePathIndex.onCreated(scenePath)

	fun getSceneDirectory(): HPath = getSceneDirectory(projectDef, fileSystem)

	fun getSceneIdFromPath(path: HPath): Int {
		val fileName = getSceneFilename(path)
		return getSceneIdFromFilename(fileName)
	}

	fun resolveScenePathFromFilesystem(id: Int): HPath? = scenePathIndex.pathFor(id)

	/**
	 * Number of scenes/groups directly inside [parentPath]. Directories the scan holds no entry for
	 * are counted from disk: an empty group reads the same to the index as one it never reached,
	 * and order numbers are derived from this, so guessing zero would mis-number a real directory.
	 */
	fun countScenePathsIn(parentPath: HPath): Int =
		scenePathIndex.childCountOrNull(parentPath) ?: listChildScenePaths(parentPath).size

	private fun listChildScenePaths(parentPath: HPath): List<HPath> =
		fileSystem.list(parentPath.toOkioPath()).filterScenePathsOkio()

	private fun getAllScenePathsOkio(): List<Path> = getAllScenePaths().map { it.toOkioPath() }

	fun getAllScenePaths(): List<HPath> = scenePathIndex.paths()

	private fun scanScenePaths(): List<HPath> =
		fileSystem.listRecursively(getSceneDirectory().toOkioPath())
			.filterScenePathsOkio()
			.sortedBy { it.name }
			.toList()

	fun getAllScenes(): List<SceneItem> {
		return getAllScenePathsOkio()
			.filterScenePathsOkio()
			.map { path ->
				getSceneFromFilename(path)
			}
	}

	private fun getSceneTempFileName(sceneDef: SceneItem): String {
		return "${sceneDef.id}.md"
	}

	private fun getSceneBufferTempPath(sceneItem: SceneItem): HPath {
		val bufferPathSegment = getSceneBufferDirectory().toOkioPath()
		val fileName = getSceneTempFileName(sceneItem)
		return bufferPathSegment.div(fileName).toHPath()
	}

	private fun getSceneIdFromBufferFilename(fileName: String): Int {
		val captures = SCENE_BUFFER_FILENAME_PATTERN.matchEntire(fileName)
			?: error("Scene filename was bad: $fileName")

		try {
			val sceneId = captures.groupValues[1].toInt()
			return sceneId
		} catch (e: NumberFormatException) {
			throw InvalidSceneBufferFilename("Number format exception", fileName, e)
		} catch (e: IllegalStateException) {
			throw InvalidSceneBufferFilename("Invalid filename", fileName, e)
		}
	}

	fun getSceneTempBufferContents(): List<SceneContent> {
		val bufferDirectory = getSceneBufferDirectory().toOkioPath()
		return fileSystem.list(bufferDirectory)
			.filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }
			.mapNotNull { path ->
				val sceneId = getSceneIdFromBufferFilename(path.name)
				resolveScenePathFromFilesystem(sceneId)?.let { scenePath ->
					getSceneFromFilename(scenePath)
				}
			}
			.map { sceneDef ->
				val tempPath = getSceneBufferTempPath(sceneDef).toOkioPath()
				val content = try {
					fileSystem.read(tempPath) {
						readUtf8()
					}
				} catch (e: IOException) {
					Napier.e("Failed to load Scene (${sceneDef.name})")
					""
				}
				SceneContent(sceneDef, content)
			}
	}

	fun loadSceneTree(rootScene: SceneItem): TreeNode<SceneItem> {
		val sceneDirPath = getSceneDirectory().toOkioPath()
		val rootNode = TreeNode(rootScene)

		val childNodes = fileSystem.list(sceneDirPath)
			.filterScenePathsOkio()
			.validateScenePaths()
			.map { path -> loadSceneTreeNode(path) }

		for (child in childNodes) {
			rootNode.addChild(child)
		}

		return rootNode
	}

	private fun loadSceneTreeNode(root: HPath): TreeNode<SceneItem> {
		val scene = getSceneFromPath(root)
		val node = TreeNode(scene)

		val rootPath = root.toOkioPath()
		if (fileSystem.metadata(rootPath).isDirectory) {
			val childNodes = fileSystem.list(rootPath)
				.filterScenePathsOkio()
				.validateScenePaths()
				.map { path -> loadSceneTreeNode(path) }

			for (child in childNodes) {
				node.addChild(child)
			}
		}

		return node
	}

	@Throws(InvalidSceneFilename::class)
	fun getSceneFromFilename(path: HPath): SceneItem {
		val fileName = getSceneFilename(path)

		val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
			?: LEGACY_SCENE_FILENAME_PATTERN.matchEntire(fileName)
			?: error("Scene filename was bad: $fileName")

		try {
			val sceneOrder = captures.groupValues[1].toInt()
			val sceneName = ProjectsRepository.decodeFromFilename(captures.groupValues[2])
			val sceneId = captures.groupValues[3].toInt()
			val isSceneGroup = !(captures.groupValues.size >= 5
				&& captures.groupValues[4] == SCENE_FILENAME_EXTENSION)

			val sceneItem = SceneItem(
				projectDef = projectDef,
				type = if (isSceneGroup) SceneItem.Type.Group else SceneItem.Type.Scene,
				id = sceneId,
				name = sceneName,
				order = sceneOrder,
			)

			return sceneItem
		} catch (e: NumberFormatException) {
			throw InvalidSceneFilename("Number format exception", fileName)
		} catch (e: IllegalStateException) {
			throw InvalidSceneFilename("Invalid filename", fileName)
		}
	}

	private fun getScenePathsFromFilesystem(root: HPath): List<Path> {
		val scenePaths = fileSystem.list(root.toOkioPath())
			.filterScenePathsOkio()
			.map { it.toOkioPath() }
		return scenePaths
	}

	fun getGroupChildPathsById(root: HPath): Map<Int, HPath> {
		return getScenePathsFromFilesystem(root)
			.map { scenePath ->
				val sceneId = getSceneIdFromPath(scenePath.toHPath())
				Pair(sceneId, scenePath)
			}.associateBy({ it.first }, { it.second.toHPath() })
	}

	fun moveScene(sourcePath: HPath, targetPath: HPath) {
		fileSystem.atomicMove(sourcePath.toOkioPath(), targetPath.toOkioPath())
		scenePathIndex.invalidate()
	}

	fun getSceneBufferDirectory(): HPath {
		val projOkPath = projectDef.path.toOkioPath()
		val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
		val bufferPathSegment = sceneDirPath.div(BUFFER_DIRECTORY)
		if (!fileSystem.exists(bufferPathSegment)) {
			fileSystem.createDirectories(bufferPathSegment)
		}
		return bufferPathSegment.toHPath()
	}

	fun getSceneFromPath(path: HPath): SceneItem {
		val sceneDef = getSceneFromFilename(path)
		return sceneDef
	}

	fun getSceneFilename(path: HPath) = path.toOkioPath().name

	fun getLastOrderNumber(parentPath: HPath): Int = countScenePathsIn(parentPath)

	fun clearTempScene(sceneItem: SceneItem) {
		val path = getSceneBufferTempPath(sceneItem).toOkioPath()
		fileSystem.delete(path)
	}

	fun loadSceneMarkdownRaw(sceneItem: SceneItem, scenePath: HPath): String {
		val content = if (sceneItem.type == SceneItem.Type.Scene) {
			try {
				fileSystem.read(scenePath.toOkioPath()) {
					readUtf8()
				}
			} catch (e: IOException) {
				Napier.e("Failed to load Scene markdown raw (${sceneItem.name})")
				""
			}
		} else {
			""
		}

		return content
	}

	suspend fun storeSceneMarkdownRaw(sceneItem: SceneContent, scenePath: HPath): Boolean {
		sceneItem.markdown ?: return false

		return try {
			fileSystem.write(scenePath.toOkioPath()) {
				writeUtf8(sceneItem.markdown)
			}

			true
		} catch (e: IOException) {
			Napier.e("Failed to store Scene markdown raw (${sceneItem.scene.id} - ${sceneItem.scene.name}) because: ${e.message}")
			false
		}
	}

	fun loadSceneBuffer(scenePath: HPath): String {
		return try {
			fileSystem.read(scenePath.toOkioPath()) {
				readUtf8()
			}
		} catch (e: IOException) {
			Napier.e("Failed to load Scene at: $scenePath")
			""
		}
	}

	fun countScenes(parentPath: HPath): Int = countScenePathsIn(parentPath) - 1

	fun storeTempSceneBuffer(buffer: SceneBuffer): Boolean {
		val scenePath = getSceneBufferTempPath(buffer.content.scene).toOkioPath()

		return try {
			val markdown = buffer.content.coerceMarkdown()

			fileSystem.write(scenePath) {
				writeUtf8(markdown)
			}

			Napier.d("Stored temp scene: (${buffer.content.scene.name})")

			true
		} catch (e: IOException) {
			Napier.e("Failed to store temp scene: (${buffer.content.scene.name}) with error: ${e.message}")
			false
		}
	}

	suspend fun storeSceneBuffer(buffer: SceneBuffer, scenePath: HPath): Boolean {

		return try {
			val markdown = buffer.content.coerceMarkdown()

			fileSystem.write(scenePath.toOkioPath()) {
				writeUtf8(markdown)
			}

			true
		} catch (e: IOException) {
			Napier.e("Failed to store scene: (${buffer.content.scene.name}) with error: ${e.message}")
			false
		}
	}

	fun createNewScene(scenePath: HPath) {
		fileSystem.createDirectory(scenePath.toOkioPath(), true)
		addScenePathToCache(scenePath)
	}

	fun createNewGroup(scenePath: HPath) {
		fileSystem.write(scenePath.toOkioPath(), true) {
			writeUtf8("")
		}
		addScenePathToCache(scenePath)
	}

	suspend fun deleteScene(scene: SceneItem): Boolean {
		val scenePath = resolveScenePathFromFilesystemIncludingArchived(scene.id)?.toOkioPath() ?: return false

		return try {
			if (!fileSystem.exists(scenePath)) {
				Napier.e("Tried to delete Scene, but file did not exist")
				false
			} else if (!fileSystem.metadata(scenePath).isRegularFile) {
				Napier.e("Tried to delete Scene, but file was not File")
				false
			} else {
				fileSystem.delete(scenePath)
				invalidateScenePathCache()
				true
			}
		} catch (e: IOException) {
			Napier.e("Failed to delete Group ID ${scene.id}: ${e.message}")
			false
		}
	}

	suspend fun deleteGroup(scene: SceneItem): Boolean {
		val scenePath = resolveScenePathFromFilesystem(scene.id)?.toOkioPath() ?: return false
		return try {
			if (!fileSystem.exists(scenePath)) {
				Napier.e("Tried to delete Group, but file did not exist")
				false
			} else if (!fileSystem.metadata(scenePath).isDirectory) {
				Napier.e("Tried to delete Group, but file was not Directory")
				false
			} else if (fileSystem.list(scenePath).isNotEmpty()) {
				Napier.w("Tried to delete Group, but was not empty")
				false
			} else {
				fileSystem.delete(scenePath)
				invalidateScenePathCache()
				true
			}
		} catch (e: IOException) {
			Napier.e("Failed to delete Group ID ${scene.id}: ${e.message}")
			false
		}
	}

	companion object {
		// Active scene format: order~name~id.md (delimiter changed from `-` to `~` in data v2).
		// The name group accepts any char except path separators and the delimiter — encoded
		// lookalikes pass through naturally.
		val SCENE_FILENAME_PATTERN = Regex("""(\d+)~([^~/\\]+)~(\d+)(\.md)?(?:\.temp)?""")

		// Archived: name~id.md (no order prefix)
		val ARCHIVED_SCENE_FILENAME_PATTERN = Regex("""([^~/\\]+)~(\d+)(\.md)(?:\.temp)?""")

		// Pre-v2 patterns. Kept for read compatibility (e.g. fixtures, projects mid-migration).
		// Old name set was the restricted `[\d\p{L}+ _']` so `-` is unambiguously a delimiter.
		val LEGACY_SCENE_FILENAME_PATTERN = Regex("""(\d+)-([\d\p{L}+ _']+)-(\d+)(\.md)?(?:\.temp)?""")
		val LEGACY_ARCHIVED_SCENE_FILENAME_PATTERN = Regex("""([\d\p{L}+ _']+)-(\d+)(\.md)(?:\.temp)?""")

		val SCENE_BUFFER_FILENAME_PATTERN = Regex("""(\d+)\.md""")
		const val SCENE_FILENAME_EXTENSION = ".md"
		const val SCENE_DIRECTORY = "scenes"
		const val BUFFER_DIRECTORY = ".buffers"
		const val ARCHIVED_DIRECTORY = ".archived"

		fun getSceneIdFromFilename(fileName: String): Int {
			val activeCaptures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
				?: LEGACY_SCENE_FILENAME_PATTERN.matchEntire(fileName)
			val archivedCaptures = ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(fileName)
				?: LEGACY_ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(fileName)

			val idString = if (activeCaptures != null) {
				activeCaptures.groupValues[3]
			} else if (archivedCaptures != null) {
				archivedCaptures.groupValues[2]
			} else {
				null
			}

			val sceneId = idString?.toIntOrNull()
			if (sceneId == null) {
				throw InvalidSceneFilename("Invalid filename", fileName)
			}

			return sceneId
		}

		fun validateSceneFilename(fileName: String): Boolean {
			return SCENE_FILENAME_PATTERN.matchEntire(fileName) != null ||
				LEGACY_SCENE_FILENAME_PATTERN.matchEntire(fileName) != null
		}

		fun validateArchivedSceneFilename(fileName: String): Boolean {
			return ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(fileName) != null ||
				LEGACY_ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(fileName) != null
		}

		fun buildArchivedSceneFileName(name: String, id: Int): String {
			return "${ProjectsRepository.encodeForFilename(name)}~$id$SCENE_FILENAME_EXTENSION"
		}

		fun getSceneDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val projOkPath = projectDef.path.toOkioPath()
			val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
			if (!fileSystem.exists(sceneDirPath)) {
				fileSystem.createDirectories(sceneDirPath)
			}
			return sceneDirPath.toHPath()
		}

		fun getArchivedDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val sceneDir = getSceneDirectory(projectDef, fileSystem).toOkioPath()
			val archivedDirPath = sceneDir.div(ARCHIVED_DIRECTORY)
			if (!fileSystem.exists(archivedDirPath)) {
				fileSystem.createDirectories(archivedDirPath)
			}
			return archivedDirPath.toHPath()
		}
	}

	fun getArchivedDirectory(): HPath = getArchivedDirectory(projectDef, fileSystem)

	fun archiveScene(scene: SceneItem): HPath {
		val currentPath = resolveScenePathFromFilesystem(scene.id)
			?: error("Scene ${scene.id} not found on filesystem")

		val archiveDir = getArchivedDirectory().toOkioPath()
		val fileName = buildArchivedSceneFileName(scene.name, scene.id)
		val targetPath = (archiveDir / fileName).toHPath()

		moveScene(currentPath, targetPath)
		return targetPath
	}

	fun unarchiveScene(scene: SceneItem, newOrder: Int): HPath {
		val currentPath = resolveScenePathFromFilesystemIncludingArchived(scene.id)
			?: error("Scene ${scene.id} not found in archive")

		val sceneDir = getSceneDirectory().toOkioPath()
		val newFileName = buildSceneFileName(newOrder, scene.name, scene.id)
		val targetPath = (sceneDir / newFileName).toHPath()

		moveScene(currentPath, targetPath)
		return targetPath
	}

	/**
	 * Create a scene file directly in the archive directory.
	 * Used when syncing an archived scene that doesn't exist locally.
	 */
	fun createArchivedSceneFile(sceneItem: SceneItem, content: String): HPath {
		val archiveDir = getArchivedDirectory().toOkioPath()
		val fileName = buildArchivedSceneFileName(sceneItem.name, sceneItem.id)
		val targetPath = archiveDir / fileName

		fileSystem.write(targetPath) {
			writeUtf8(content)
		}

		return targetPath.toHPath()
	}

	private fun buildSceneFileName(order: Int, name: String, id: Int): String {
		return "$order~${ProjectsRepository.encodeForFilename(name)}~$id$SCENE_FILENAME_EXTENSION"
	}

	fun getAllArchivedScenePaths(): List<HPath> {
		val archivedDir = getArchivedDirectory().toOkioPath()
		if (!fileSystem.exists(archivedDir)) return emptyList()

		return fileSystem.list(archivedDir)
			.filter { validateArchivedSceneFilename(it.name) }
			.sortedBy { it.name }
			.map { it.toHPath() }
	}

	/**
	 * Parse a scene from the archived filename format (name-id.md).
	 */
	private fun getSceneFromArchivedFilename(path: HPath): SceneItem {
		val fileName = getSceneFilename(path)
		val captures = ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(fileName)
			?: LEGACY_ARCHIVED_SCENE_FILENAME_PATTERN.matchEntire(fileName)
			?: error("Archived scene filename was invalid: $fileName")

		val sceneName = ProjectsRepository.decodeFromFilename(captures.groupValues[1])
		val sceneId = captures.groupValues[2].toInt()

		return SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene, // Archived scenes are always scenes, not groups
			id = sceneId,
			name = sceneName,
			order = 0, // Order is meaningless for archived scenes
			archived = true
		)
	}

	fun getArchivedScenes(): List<SceneItem> {
		return getAllArchivedScenePaths().map { path ->
			getSceneFromArchivedFilename(path)
		}
	}

	fun resolveScenePathFromFilesystemIncludingArchived(id: Int): HPath? {
		val regularPath = resolveScenePathFromFilesystem(id)
		if (regularPath != null) return regularPath

		return getAllArchivedScenePaths().find { path ->
			getSceneIdFromPath(path) == id
		}
	}
}

internal fun Path.isInArchivedDirectory(): Boolean {
	val pathStr = this.toString()
	return pathStr.contains("${Path.DIRECTORY_SEPARATOR}${SceneDatasource.ARCHIVED_DIRECTORY}${Path.DIRECTORY_SEPARATOR}") ||
		pathStr.contains("${Path.DIRECTORY_SEPARATOR}${SceneDatasource.ARCHIVED_DIRECTORY}") && this.parent?.name == SceneDatasource.ARCHIVED_DIRECTORY
}

private fun HPath.isInArchivedDirectory(): Boolean {
	return this.toOkioPath().isInArchivedDirectory()
}

fun Collection<Path>.filterScenePathsOkio() =
	filter { !it.isInArchivedDirectory() }
		.map { it.toHPath() }
		.filterScenePaths()

fun Sequence<Path>.filterScenePathsOkio() =
	filter { !it.isInArchivedDirectory() }
		.map { it.toHPath() }
		.filterScenePaths()

fun Collection<HPath>.filterScenePaths() = filter {
	validateSceneFilename(it.name) && !it.isInArchivedDirectory()
}.sortedBy { it.name }

fun Sequence<HPath>.filterScenePaths() = filter {
	validateSceneFilename(it.name) && !it.isInArchivedDirectory()
}.sortedBy { it.name }

// Reject scenes with duplicate IDs (first seen wins, later duplicates refused). #492
fun Collection<HPath>.validateScenePaths(): List<HPath> {
	val seen = LinkedHashMap<Int, HPath>()
	for (path in this) {
		val id = SceneDatasource.getSceneIdFromFilename(path.toOkioPath().name)
		val existing = seen[id]
		if (existing == null) {
			seen[id] = path
		} else {
			Napier.e("Duplicate scene id $id on disk: keeping ${existing.name}, refusing ${path.name}")
		}
	}
	return seen.values.toList()
}
