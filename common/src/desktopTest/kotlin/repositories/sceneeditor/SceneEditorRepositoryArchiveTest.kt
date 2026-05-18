package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import createProject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.*
import kotlin.time.Instant

class SceneEditorRepositoryArchiveTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projectPath: HPath
	private lateinit var projectsRepo: ProjectsRepository
	private lateinit var syncDataRepository: SyncDataRepository
	private lateinit var projectDef: ProjectDef
	private lateinit var repo: SceneEditorRepository
	private lateinit var idRepository: IdRepository
	private lateinit var metadataRepository: ProjectMetadataDatasource
	private lateinit var metadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource
	private lateinit var statisticsRepository: StatisticsRepository
	private var nextId = -1

	private fun claimId(): Int {
		val id = nextId
		nextId++
		return id
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()

		val rootDir = getDefaultRootDocumentDirectory()
		ffs.createDirectories(rootDir.toPath())

		nextId = 100
		idRepository = mockk()
		coEvery { idRepository.claimNextId() } answers { claimId() }
		coEvery { idRepository.findNextId() } answers { }

		metadataRepository = mockk()
		every { metadataRepository.loadMetadata(any()) } returns
			ProjectMetadata(
				info = Info(
					created = Instant.DISTANT_FUTURE,
					lastAccessed = Instant.DISTANT_FUTURE,
					dataVersion = PROJECT_DATA_VERSION,
				)
			)

		metadataDatasource = mockk(relaxed = true)
		statisticsRepository = mockk(relaxed = true)

		syncDataRepository = mockk()
		every { syncDataRepository.isServerSynchronized() } returns false

		projectsRepo = mockk()
		every { projectsRepo.getProjectsDirectory() } returns
			rootDir.toPath().div(SceneEditorRepositoryMoveTest.PROJ_DIR).toHPath()

		mockkObject(ProjectsRepository.Companion)

		setupKoin()
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		repo.onScopeClose(mockk())
		ffs.checkNoOpenFiles()
	}

	private fun configure(projectName: String) {
		projectPath = projectsRepo.getProjectsDirectory().toOkioPath().div(projectName).toHPath()

		projectDef = ProjectDef(
			name = projectName,
			path = projectPath
		)
		sceneDatasource = SceneDatasource(projectDef, ffs)

		createProject(ffs, projectName)

		repo = SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			projectMetadataDatasource = metadataRepository,
			sceneMetadataDatasource = metadataDatasource,
			sceneDatasource = sceneDatasource,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = mockk(relaxed = true),
			writingSessionTracker = mockk(relaxed = true),
		)
	}

	@Test
	fun `Archive Scene - success`() = runTest {
		configure(PROJECT_1_NAME)
		repo.initializeSceneEditor()

		// Get a scene to archive (ID 1 is a scene)
		val sceneId = 1
		val scene = repo.getSceneItemFromId(sceneId)
		assertNotNull(scene, "Scene should exist")
		assertEquals(SceneItem.Type.Scene, scene.type, "Should be a scene")

		// Archive the scene
		val result = repo.archiveScene(scene)
		assertTrue(result, "Archive should succeed")

		// Scene should no longer be in the tree
		val sceneAfterArchive = repo.getSceneItemFromId(sceneId)
		assertNull(sceneAfterArchive, "Scene should not be in tree after archiving")

		// Scene should be in archived list
		val archivedScenes = repo.getArchivedScenes()
		assertTrue(archivedScenes.any { it.id == sceneId }, "Scene should be in archived list")
	}

	@Test
	fun `Archive Group - should fail`() = runTest {
		configure(PROJECT_1_NAME)
		repo.initializeSceneEditor()

		// Get a group to try to archive (ID 2 is a group)
		val groupId = 2
		val group = repo.getSceneItemFromId(groupId)
		assertNotNull(group, "Group should exist")
		assertEquals(SceneItem.Type.Group, group.type, "Should be a group")

		// Try to archive the group - should fail
		val result = repo.archiveScene(group)
		assertFalse(result, "Archiving a group should fail")

		// Group should still be in the tree
		val groupAfterArchive = repo.getSceneItemFromId(groupId)
		assertNotNull(groupAfterArchive, "Group should still be in tree")
	}

	// Regression: when unarchiving pushes the parent's child count to a new digit
	// magnitude (e.g. 9 → 10), SceneDatasource.unarchiveScene wrote the new file
	// with an UNPADDED order, leaving the directory containing a mix of paddings
	// like "0~", "1~", ..., "8~", "10~..." — siblings sort wrong and getSceneFilePath
	// can no longer resolve them. Verify all root scene files share a padding width
	// after the unarchive completes.
	@Test
	fun `Unarchive Scene - magnitude increase repads sibling files`() = runTest {
		configure(PROJECT_1_NAME)
		repo.initializeSceneEditor()

		// Project 1 starts with 4 root entries (3 scenes + 1 group, padding=1).
		// Archive scene 1 → 3 root entries left (padding=1).
		val scene = repo.getSceneItemFromId(1)
		assertNotNull(scene)
		repo.archiveScene(scene)

		// Add scenes until the root has exactly 10 entries (orders 0..9, still padding=1
		// because createScene only re-pads when the new scene's order crosses a digit width).
		repeat(7) { i ->
			repo.createScene(null, "Filler $i")
		}

		val scenesDir = sceneDatasource.getSceneDirectory().toOkioPath()
		val rootCountBefore = ffs.list(scenesDir)
			.filter { SceneDatasource.validateSceneFilename(it.name) }
			.size
		assertEquals(10, rootCountBefore, "Setup: root should have 10 entries before unarchive")

		// Unarchive — this pushes the count to 10, so padding must grow to 2.
		val archivedScene = repo.getArchivedScenes().first { it.id == 1 }
		val unarchived = repo.unarchiveScene(archivedScene)
		assertNotNull(unarchived)

		// All root scene files should now share a consistent order width (= 2).
		val orderWidths = ffs.list(scenesDir)
			.filter { SceneDatasource.validateSceneFilename(it.name) }
			.map { SceneDatasource.SCENE_FILENAME_PATTERN.matchEntire(it.name)!!.groupValues[1].length }
			.toSet()
		assertEquals(
			setOf(2),
			orderWidths,
			"All root scene files should be 2-digit padded, got widths $orderWidths",
		)

		// And the repository's calculated path for the unarchived scene must exist on disk
		// (regression for resolveScenePathFromFilesystem failing after padding mismatch).
		val unarchivedPath = repo.getSceneFilePath(unarchived.id).toOkioPath()
		assertTrue(ffs.exists(unarchivedPath), "Calculated path '$unarchivedPath' should exist on disk")
	}

	@Test
	fun `Unarchive Scene - success`() = runTest {
		configure(PROJECT_1_NAME)
		repo.initializeSceneEditor()

		// First archive a scene
		val sceneId = 1
		val scene = repo.getSceneItemFromId(sceneId)
		assertNotNull(scene)
		repo.archiveScene(scene)

		// Verify it's archived
		assertNull(repo.getSceneItemFromId(sceneId))
		val archivedScenes = repo.getArchivedScenes()
		val archivedScene = archivedScenes.find { it.id == sceneId }
		assertNotNull(archivedScene, "Scene should be archived")

		// Unarchive the scene
		val result = repo.unarchiveScene(archivedScene)
		assertNotNull(result, "Unarchive should succeed")

		// Scene should be back in the tree at root level
		val restoredScene = repo.getSceneItemFromId(sceneId)
		assertNotNull(restoredScene, "Scene should be back in tree")
		assertFalse(restoredScene.archived, "Scene should not be marked as archived")

		// Scene should no longer be in archived list
		val archivedScenesAfter = repo.getArchivedScenes()
		assertFalse(archivedScenesAfter.any { it.id == sceneId }, "Scene should not be in archived list")
	}

	@Test
	fun `getArchivedScenes returns empty initially`() = runTest {
		configure(PROJECT_1_NAME)
		repo.initializeSceneEditor()

		val archivedScenes = repo.getArchivedScenes()
		assertTrue(archivedScenes.isEmpty(), "Should have no archived scenes initially")
	}

	@Test
	fun `Archive multiple scenes`() = runTest {
		configure(PROJECT_1_NAME)
		repo.initializeSceneEditor()

		// Archive multiple scenes
		val sceneIds = listOf(1, 4, 7) // scenes from PROJECT_1
		val archivedIds = mutableListOf<Int>()
		for (sceneId in sceneIds) {
			val scene = repo.getSceneItemFromId(sceneId)
			if (scene != null && scene.type == SceneItem.Type.Scene) {
				repo.archiveScene(scene)
				archivedIds.add(sceneId)
			}
		}

		// Verify all are archived
		val archivedScenes = repo.getArchivedScenes()
		for (sceneId in archivedIds) {
			val scene = repo.getSceneItemFromId(sceneId)
			assertNull(scene, "Scene $sceneId should not be in tree")
			assertTrue(archivedScenes.any { it.id == sceneId }, "Scene $sceneId should be archived")
		}
	}

	@Test
	fun `Archived scene path resolution includes archived`() = runTest {
		configure(PROJECT_1_NAME)
		repo.initializeSceneEditor()

		// Archive a scene
		val sceneId = 1
		val scene = repo.getSceneItemFromId(sceneId)
		assertNotNull(scene)
		repo.archiveScene(scene)

		// Regular path resolution should not find it
		val regularPath = sceneDatasource.resolveScenePathFromFilesystem(sceneId)
		assertNull(regularPath, "Regular path resolution should not find archived scene")

		// Including archived should find it
		val includingArchivedPath = repo.resolveScenePathFromFilesystemIncludingArchived(sceneId)
		assertNotNull(includingArchivedPath, "Including archived should find the scene")
	}
}
