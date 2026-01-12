package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneDatasourceArchiveTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var sceneDatasource: SceneDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createProject(ffs, PROJECT_1_NAME)
		sceneDatasource = SceneDatasource(getProjectDef(PROJECT_1_NAME), ffs)
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `getArchivedDirectory returns correct path`() = runTest {
		val archivedDir = sceneDatasource.getArchivedDirectory()
		val expectedPath = sceneDatasource.getSceneDirectory().toOkioPath() / SceneDatasource.ARCHIVED_DIRECTORY

		assertEquals(expectedPath.toHPath(), archivedDir)
	}

	@Test
	fun `archiveScene moves scene to archived directory`() = runTest {
		// Get an existing scene
		val sceneId = 1
		val originalPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertNotNull(originalPath, "Scene should exist before archiving")
		assertTrue(ffs.exists(originalPath.toOkioPath()), "Scene file should exist")

		// Get the scene item
		val scene = sceneDatasource.getSceneFromFilename(originalPath)

		// Archive the scene - returns the new path
		val archivedPath = sceneDatasource.archiveScene(scene)

		// Verify the returned path is in the archived directory
		assertTrue(
			archivedPath.path.contains(SceneDatasource.ARCHIVED_DIRECTORY),
			"Archived path should be in archived directory"
		)

		// Verify original file no longer exists
		assertFalse(ffs.exists(originalPath.toOkioPath()), "Original scene file should not exist")

		// Verify scene exists in archived directory
		val archivedDir = sceneDatasource.getArchivedDirectory().toOkioPath()
		assertTrue(ffs.exists(archivedDir), "Archived directory should exist")

		val archivedFiles = ffs.list(archivedDir)
		val archivedFile = archivedFiles.find { it.name.contains("-$sceneId") }
		assertNotNull(archivedFile, "Archived scene file should exist")
	}

	@Test
	fun `unarchiveScene moves scene back from archived directory`() = runTest {
		// First, archive a scene
		val sceneId = 1
		val originalPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertNotNull(originalPath)
		val scene = sceneDatasource.getSceneFromFilename(originalPath)
		sceneDatasource.archiveScene(scene)

		// Verify it's in archived directory
		val archivedDir = sceneDatasource.getArchivedDirectory().toOkioPath()
		assertTrue(ffs.list(archivedDir).any { it.name.contains("-$sceneId") })

		// Get the archived scene (with archived=true flag)
		val archivedScenes = sceneDatasource.getArchivedScenes()
		val archivedScene = archivedScenes.find { it.id == sceneId }
		assertNotNull(archivedScene, "Should find archived scene")
		assertTrue(archivedScene.archived, "Archived scene should have archived flag")

		// Unarchive the scene - returns new path
		val newOrder = 10
		val unarchivedPath = sceneDatasource.unarchiveScene(archivedScene, newOrder)

		// Verify the returned path is in the scenes directory (not archived)
		assertFalse(
			unarchivedPath.path.contains(SceneDatasource.ARCHIVED_DIRECTORY),
			"Unarchived path should not be in archived directory"
		)

		// Verify scene no longer exists in archived directory
		val archivedFilesAfter = ffs.list(archivedDir)
		assertFalse(
			archivedFilesAfter.any { it.name.contains("-$sceneId") },
			"Scene should not be in archived directory"
		)

		// Verify scene exists back in scenes directory
		val restoredPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertNotNull(restoredPath, "Scene should exist in scenes directory")
		assertTrue(ffs.exists(restoredPath.toOkioPath()), "Restored scene file should exist")
	}

	@Test
	fun `getArchivedScenes returns only archived scenes`() = runTest {
		// Initially should have no archived scenes
		val initialArchived = sceneDatasource.getArchivedScenes()
		assertTrue(initialArchived.isEmpty(), "Should have no archived scenes initially")

		// Archive a scene
		val sceneId = 1
		val originalPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertNotNull(originalPath)
		val scene = sceneDatasource.getSceneFromFilename(originalPath)
		sceneDatasource.archiveScene(scene)

		// Now should have one archived scene
		val archivedScenes = sceneDatasource.getArchivedScenes()
		assertEquals(1, archivedScenes.size, "Should have one archived scene")
		assertEquals(sceneId, archivedScenes.first().id, "Archived scene should have correct ID")
		assertTrue(archivedScenes.first().archived, "Archived scene should be marked as archived")
	}

	@Test
	fun `getAllScenePaths excludes archived scenes`() = runTest {
		// Get initial scene count
		val initialPaths = sceneDatasource.getAllScenePaths()
		val initialCount = initialPaths.size

		// Archive a scene
		val sceneId = 1
		val originalPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertNotNull(originalPath)
		val scene = sceneDatasource.getSceneFromFilename(originalPath)
		sceneDatasource.archiveScene(scene)

		// Get paths after archiving - should be one less
		val pathsAfter = sceneDatasource.getAllScenePaths()
		assertEquals(initialCount - 1, pathsAfter.size, "getAllScenePaths should exclude archived scenes")

		// Verify the archived scene ID is not in the paths
		val ids = pathsAfter.map { sceneDatasource.getSceneIdFromPath(it) }
		assertFalse(ids.contains(sceneId), "Archived scene should not be in getAllScenePaths")
	}

	@Test
	fun `resolveScenePathFromFilesystemIncludingArchived finds archived scenes`() = runTest {
		// Archive a scene
		val sceneId = 1
		val originalPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertNotNull(originalPath)
		val scene = sceneDatasource.getSceneFromFilename(originalPath)
		sceneDatasource.archiveScene(scene)

		// Regular resolve should not find it
		val regularPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertEquals(null, regularPath, "Regular resolve should not find archived scene")

		// Including archived should find it
		val includingArchivedPath = sceneDatasource.resolveScenePathFromFilesystemIncludingArchived(sceneId)
		assertNotNull(includingArchivedPath, "Including archived should find the scene")
		assertTrue(
			includingArchivedPath.path.contains(SceneDatasource.ARCHIVED_DIRECTORY),
			"Path should be in archived directory"
		)
	}

	@Test
	fun `archiveScene works on Group type at datasource level`() = runTest {
		// Try to archive a group (ID 2 is a group in PROJECT_1)
		val groupId = 2
		val groupPath = sceneDatasource.resolveScenePathFromFilesystem(groupId)
		assertNotNull(groupPath)
		val group = sceneDatasource.getSceneFromFilename(groupPath)
		assertEquals(SceneItem.Type.Group, group.type, "Should be a group")

		// Archiving a group at datasource level just moves the file
		// The repository layer should prevent this
		val archivedPath = sceneDatasource.archiveScene(group)

		// The file should have been moved
		assertNotNull(archivedPath)
		assertTrue(archivedPath.path.contains(SceneDatasource.ARCHIVED_DIRECTORY))
	}
}
