package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource.Companion.validateSceneFilename
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
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

	fun getSceneDirectory(): HPath = getSceneDirectory(projectDef, fileSystem)

	fun getSceneIdFromPath(path: HPath): Int {
		val fileName = getSceneFilename(path)
		return getSceneIdFromFilename(fileName)
	}

	fun resolveScenePathFromFilesystem(id: Int, paths: List<HPath> = getAllScenePaths()): HPath? {
		return paths.find { path -> getSceneIdFromPath(path) == id }
	}

	fun getPathFromFilesystem(sceneItem: SceneItem): HPath? {
		return getAllScenePathsOkio()
			.filterScenePathsOkio().firstOrNull { path ->
				sceneItem.id == getSceneFromFilename(path).id
			}
	}

	private fun getAllScenePathsOkio(): List<Path> {
		val sceneDirPath = getSceneDirectory().toOkioPath()
		val scenePaths = fileSystem.listRecursively(sceneDirPath)
			.filterScenePathsOkio()
			.sortedBy { it.name }
			.map { it.toOkioPath() }
			.toList()
		return scenePaths
	}

	fun getAllScenePaths(): List<HPath> {
		val sceneDirPath = getSceneDirectory().toOkioPath()
		val scenePaths = fileSystem.listRecursively(sceneDirPath)
			.filterScenePathsOkio()
			.sortedBy { it.name }
			.toList()
		return scenePaths
	}

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
			?: throw IllegalStateException("Scene filename was bad: $fileName")

		try {
			val sceneId = captures.groupValues[1].toInt()
			return sceneId
		} catch (e: NumberFormatException) {
			throw InvalidSceneBufferFilename("Number format exception", fileName)
		} catch (e: IllegalStateException) {
			throw InvalidSceneBufferFilename("Invalid filename", fileName)
		}
	}

	fun getSceneTempBufferContents(): List<SceneContent> {
		val bufferDirectory = getSceneBufferDirectory().toOkioPath()
		return fileSystem.list(bufferDirectory)
			.filter { fileSystem.metadata(it).isRegularFile }
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

	fun exportStory(path: HPath, allNodes: List<TreeValue<SceneItem>>): HPath {
		val exportPath = path.toOkioPath() / getExportStoryFileName()
		val allPaths = getAllScenePaths()

		fileSystem.write(exportPath) {
			writeUtf8("# ${projectDef.name}\n\n")

			allNodes.forEachIndexed { index, chapterNode ->
				val scene = chapterNode.value

				val chapterNumber = index + 1

				writeUtf8("\n## $chapterNumber. ${scene.name}\n\n")

				val scenePath = resolveScenePathFromFilesystem(chapterNode.value.id, allPaths)
					?: error("Could not find Scene for ID ${chapterNode.value.id}")

				if (scene.type == SceneItem.Type.Scene) {
					val markdown = loadSceneMarkdownRaw(chapterNode.value, scenePath)
					writeUtf8(markdown)
					writeUtf8("\n")
				} else {
					chapterNode.filter { it.value.type == SceneItem.Type.Scene }
						.forEach { sceneNode ->
							val childScenePath =
								resolveScenePathFromFilesystem(sceneNode.value.id, allPaths)
									?: error("Could not find Scene for ID ${sceneNode.value.id}")

							val markdown = loadSceneMarkdownRaw(sceneNode.value, childScenePath)
							writeUtf8(markdown)
							writeUtf8("\n")
						}
				}
			}
		}

		return exportPath.toHPath()
	}

	fun loadSceneTree(rootScene: SceneItem): TreeNode<SceneItem> {
		val sceneDirPath = getSceneDirectory().toOkioPath()
		val rootNode = TreeNode(rootScene)

		val childNodes = fileSystem.list(sceneDirPath)
			.filterScenePathsOkio()
			.map { it.toOkioPath() }
			.map { path -> loadSceneTreeNode(path.toHPath()) }

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
			?: throw IllegalStateException("Scene filename was bad: $fileName")

		try {
			val sceneOrder = captures.groupValues[1].toInt()
			val sceneName = captures.groupValues[2]
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
	}

	fun getSceneBufferDirectory(): HPath {
		val projOkPath = projectDef.path.toOkioPath()
		val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
		val bufferPathSegment = sceneDirPath.div(BUFFER_DIRECTORY)
		if (!fileSystem.exists(bufferPathSegment)) {
			fileSystem.createDirectory(bufferPathSegment)
		}
		return bufferPathSegment.toHPath()
	}

	fun getSceneFromPath(path: HPath): SceneItem {
		val sceneDef = getSceneFromFilename(path)
		return sceneDef
	}

	fun getSceneFilename(path: HPath) = path.toOkioPath().name

	fun getExportStoryFileName() = "${projectDef.name}.md"

	fun getLastOrderNumber(parentPath: HPath): Int {
		val numScenes = fileSystem.list(parentPath.toOkioPath())
			.filterScenePathsOkio()
			.count()
		return numScenes
	}

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

	fun countScenes(parentPath: HPath): Int {
		return fileSystem.list(parentPath.toOkioPath())
			.filterScenePathsOkio()
			.count() - 1
	}

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
	}

	fun createNewGroup(scenePath: HPath) {
		fileSystem.write(scenePath.toOkioPath(), true) {
			writeUtf8("")
		}
	}

	suspend fun deleteScene(scene: SceneItem): Boolean {
		val scenePath = resolveScenePathFromFilesystem(scene.id)?.toOkioPath() ?: return false

		return try {
			if (!fileSystem.exists(scenePath)) {
				Napier.e("Tried to delete Scene, but file did not exist")
				false
			} else if (!fileSystem.metadata(scenePath).isRegularFile) {
				Napier.e("Tried to delete Scene, but file was not File")
				false
			} else {
				fileSystem.delete(scenePath)
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
				true
			}
		} catch (e: IOException) {
			Napier.e("Failed to delete Group ID ${scene.id}: ${e.message}")
			false
		}
	}

	companion object {
		val SCENE_FILENAME_PATTERN = Regex("""(\d+)-([\d\p{L}+ _']+)-(\d+)(\.md)?(?:\.temp)?""")
		val SCENE_BUFFER_FILENAME_PATTERN = Regex("""(\d+)\.md""")
		const val SCENE_FILENAME_EXTENSION = ".md"
		const val SCENE_DIRECTORY = "scenes"
		const val BUFFER_DIRECTORY = ".buffers"

		fun getSceneIdFromFilename(fileName: String): Int {
			val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
				?: throw IllegalStateException("Scene filename was bad: $fileName")
			try {
				val sceneId = captures.groupValues[3].toInt()
				return sceneId
			} catch (e: NumberFormatException) {
				throw InvalidSceneFilename("Number format exception", fileName)
			} catch (e: IllegalStateException) {
				throw InvalidSceneFilename("Invalid filename", fileName)
			}
		}

		fun validateSceneFilename(fileName: String): Boolean {
			return SCENE_FILENAME_PATTERN.matchEntire(fileName) != null
		}

		fun getSceneDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val projOkPath = projectDef.path.toOkioPath()
			val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
			if (!fileSystem.exists(sceneDirPath)) {
				fileSystem.createDirectories(sceneDirPath)
			}
			return sceneDirPath.toHPath()
		}
	}
}

fun Collection<Path>.filterScenePathsOkio() =
	map { it.toHPath() }.filterScenePaths()

fun Sequence<Path>.filterScenePathsOkio() =
	map { it.toHPath() }.filterScenePaths()

fun Collection<HPath>.filterScenePaths() = filter {
	validateSceneFilename(it.name)
}.sortedBy { it.name }

fun Sequence<HPath>.filterScenePaths() = filter {
	validateSceneFilename(it.name)
}.sortedBy { it.name }
