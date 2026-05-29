package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Migrated from SceneEditorRepositoryBufferTest after the Phase C extraction.
 *
 * Buffer state, the autosave/debounce engine, and dirty tracking are asserted directly against
 * [SceneContentRepository]. Save behavior with side-effects (timestamps, stats) is exercised
 * through [SceneEditorService] (the orchestrator). A handful of grab-bag structural assertions
 * that lived in the old buffer test still go through [SceneEditorRepository].
 */
class SceneContentRepositoryTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@MockK
	private lateinit var syncDataRepository: SyncDataRepository

	@MockK
	private lateinit var idRepository: IdRepository

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource
	private lateinit var statisticsRepository: StatisticsRepository

	private lateinit var contentRepo: SceneContentRepository
	private lateinit var repo: SceneEditorRepository
	private lateinit var service: SceneEditorService

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()

		statisticsRepository = mockk(relaxed = true)

		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns ProjectMetadata(
			info = Info(
				created = Instant.parse("2022-01-01T00:00:00.000Z"),
				dataVersion = 1,
			)
		)
		coEvery { syncDataRepository.isServerSynchronized() } returns false
		coEvery { syncDataRepository.isEntityDirty(any()) } returns false
		coEvery { syncDataRepository.markEntityAsDirty(any(), any()) } just Runs
	}

	private fun createStack(projectDef: ProjectDef) {
		sceneMetadataDatasource = SceneMetadataDatasource(ffs, toml, projectDef)
		sceneDatasource = SceneDatasource(projectDef, ffs)
		contentRepo = SceneContentRepository(projectDef, sceneDatasource)
		val sceneMetadataRepository = SceneMetadataRepository(
			projectDef = projectDef,
			sceneMetadataDatasource = sceneMetadataDatasource,
			projectMetadataDatasource = projectMetadataDatasource,
			strRes = mockk(relaxed = true),
			clock = Clock.System,
		)
		val referenceIndexRepository = mockk<ReferenceIndexRepository>(relaxed = true)
		val writingSessionTracker = mockk<WritingSessionTracker>(relaxed = true)
		repo = SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			sceneMetadataRepository = sceneMetadataRepository,
			sceneContentRepository = contentRepo,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = referenceIndexRepository,
			writingSessionTracker = writingSessionTracker,
			clock = Clock.System,
		)
		service = SceneEditorService(
			sceneEditorRepository = repo,
			sceneContentRepository = contentRepo,
			sceneMetadataRepository = sceneMetadataRepository,
			referenceIndexRepository = referenceIndexRepository,
			statisticsRepository = statisticsRepository,
			writingSessionTracker = writingSessionTracker,
		)
	}

	@Test
	fun `Subscribe to Buffer Updates`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val newContent = SceneContent(
			scene = SceneItem(getProject1Def(), SceneItem.Type.Scene, 1, "Scene ID 1", 0),
			markdown = "New Content!!"
		)

		val onBufferUpdate: (suspend (SceneBuffer) -> Unit) = mockk()
		val sceneBufferSlot = slot<SceneBuffer>()
		coEvery { onBufferUpdate(capture(sceneBufferSlot)) } just Runs

		val subJob = contentRepo.subscribeToBufferUpdates(null, scope, onBufferUpdate)

		contentRepo.onContentChanged(newContent, UpdateSource.Editor)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(atLeast = 1) { onBufferUpdate(any()) }

		assertTrue(sceneBufferSlot.isCaptured)
		val updated = sceneBufferSlot.captured
		assertEquals(newContent.scene, updated.content.scene)
		assertEquals(UpdateSource.Editor, updated.source)
		assertEquals(newContent.markdown, updated.content.markdown)
	}

	@Test
	fun `Subscribe to Buffer Updates for one scene`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val sceneItem2 = SceneItem(getProject1Def(), SceneItem.Type.Scene, 3, "Scene ID 3", 0)
		val newContent = SceneContent(
			scene = SceneItem(getProject1Def(), SceneItem.Type.Scene, 1, "Scene ID 1", 0),
			markdown = "New Content!!"
		)

		val onBufferUpdate: (suspend (SceneBuffer) -> Unit) = mockk()
		coEvery { onBufferUpdate(any()) } just Runs

		val subJob = contentRepo.subscribeToBufferUpdates(sceneItem2, scope, onBufferUpdate)
		contentRepo.onContentChanged(newContent, UpdateSource.Editor)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(exactly = 0) { onBufferUpdate(any()) }
	}

	@Test
	fun `Subscribe to Scene Updates`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val onSceneUpdate: ((SceneSummary) -> Unit) = mockk()
		coEvery { onSceneUpdate(any()) } just Runs

		val subJob = repo.subscribeToSceneUpdates(scope, onSceneUpdate)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(atLeast = 1) { onSceneUpdate(any()) }
	}

	@Test
	fun `Store Scene Buffer when no buffer is loaded`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(getProject1Def(), SceneItem.Type.Scene, 3, "Scene ID 3", 0)

		val stored = service.storeSceneBuffer(sceneItem)
		assertFalse(stored)
	}

	@Test
	fun `Store Scene Buffer raw`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(getProject1Def(), SceneItem.Type.Scene, 3, "Scene ID 3", 0)
		val content = SceneContent(scene = sceneItem, markdown = "Updated scene content ID 3")

		val stored = repo.storeSceneMarkdownRaw(content)
		assertTrue(stored)

		val scene3Path = repo.getSceneFilePath(3).toOkioPath()
		ffs.read(scene3Path) {
			assertEquals(content.markdown, readUtf8())
		}
	}

	@Test
	fun `Get scene path from filesystem`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		val scene3Path = repo.resolveScenePathFromFilesystem(3)?.toOkioPath()
		assertNotNull(scene3Path)

		val pathSegments = scene3Path.segments.reversed()
		assertEquals("0-Scene ID 3-3.md", pathSegments[0])
		assertEquals("1-Chapter ID 2-2", pathSegments[1])
		assertEquals("scenes", pathSegments[2])
	}

	@Test
	fun `Load Scene Buffer, then store it`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(getProject1Def(), SceneItem.Type.Scene, 3, "Scene ID 3", 0)

		val buffer = service.loadSceneBuffer(sceneItem)
		assertEquals(sceneItem, buffer.content.scene)
		assertEquals("Content of scene id 3", buffer.content.markdown)

		val stored = service.storeSceneBuffer(sceneItem)
		assertTrue(stored)
	}

	@Test
	fun `No dirty buffers on first load`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		assertFalse(contentRepo.hasDirtyBuffers())
	}

	private fun getTempBufferPath(sceneId: Int): Path {
		val bufferDir = sceneDatasource.getSceneBufferDirectory().toOkioPath()
		ffs.createDirectories(bufferDir)
		return bufferDir / "$sceneId.md"
	}

	private fun content(sceneId: Int) = "This is _test_ temp buffer content for Scene $sceneId"

	private fun writeTempBuffer(sceneId: Int) {
		val tempBufPath = getTempBufferPath(sceneId)
		ffs.write(tempBufPath) {
			writeUtf8(content(sceneId))
		}
	}

	@Test
	fun `Dirty buffer on first load because of temp buffer`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		writeTempBuffer(1)

		repo.initializeSceneEditor()

		assertTrue(contentRepo.hasDirtyBuffers())
		assertTrue(contentRepo.hasDirtyBuffer(1))
		assertFalse(contentRepo.hasDirtyBuffer(2))
	}

	@Test
	fun `Discard Dirty buffer`() = runTest(mainTestDispatcher) {
		val sceneId = 1
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		writeTempBuffer(sceneId)

		repo.initializeSceneEditor()
		assertTrue(contentRepo.hasDirtyBuffer(sceneId))

		val sceneItem = SceneItem(getProject1Def(), SceneItem.Type.Scene, sceneId, "Scene ID $sceneId", 0)
		service.discardSceneBuffer(sceneItem)

		assertFalse(contentRepo.hasDirtyBuffer(1))

		val temp2Path = getTempBufferPath(1)
		assertFalse(ffs.exists(temp2Path))
	}

	@Test
	fun `Store all dirty buffers`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		writeTempBuffer(1)
		writeTempBuffer(3)

		repo.initializeSceneEditor()

		assertTrue(contentRepo.hasDirtyBuffers())
		assertTrue(contentRepo.hasDirtyBuffer(1))
		assertTrue(contentRepo.hasDirtyBuffer(3))

		service.storeAllBuffers()

		assertFalse(ffs.exists(getTempBufferPath(1)))
		assertFalse(ffs.exists(getTempBufferPath(3)))

		assertFalse(contentRepo.hasDirtyBuffers())
		assertFalse(contentRepo.hasDirtyBuffer(1))
		assertFalse(contentRepo.hasDirtyBuffer(3))

		val scene1Path = repo.getSceneFilePath(1).toOkioPath()
		ffs.read(scene1Path) {
			assertEquals(content(1), readUtf8())
		}

		val scene2Path = repo.getSceneFilePath(3).toOkioPath()
		ffs.read(scene2Path) {
			assertEquals(content(3), readUtf8())
		}
	}

	@Test
	fun `Resolve path for scene ID`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		val path = repo.resolveScenePathFromFilesystem(3)?.toOkioPath()
		assertNotNull(path)

		val pathSegments = path.segments.reversed()
		assertEquals("0-Scene ID 3-3.md", pathSegments[0])
		assertEquals("1-Chapter ID 2-2", pathSegments[1])
		assertEquals("scenes", pathSegments[2])
	}

	@Test
	fun `Get all scenes`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val scenes = repo.getScenes()
		assertEquals(
			listOf(
				getSceneItem(1, 0),
				getSceneItem(2, 1, SceneItem.Type.Group),
				getSceneItem(6, 2),
				getSceneItem(7, 3),
				getSceneItem(3, 0),
				getSceneItem(4, 1),
				getSceneItem(5, 2),
			).sortedBy { it.id },
			scenes.sortedBy { it.id }
		)
	}

	private fun getSceneItem(
		id: Int,
		order: Int,
		type: SceneItem.Type = SceneItem.Type.Scene
	): SceneItem {
		return SceneItem(
			projectDef = getProject1Def(),
			type = type,
			id = id,
			name = if (type == SceneItem.Type.Scene) "Scene ID $id" else "Chapter ID $id",
			order = order
		)
	}

	@Test
	fun `Rationalize Tree`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		createStack(projDef)
		repo.initializeSceneEditor()

		val oldPath1 = repo.getSceneFilePath(1).toOkioPath()
		val oldPath6 = repo.getSceneFilePath(6).toOkioPath()

		assertTrue(ffs.exists(oldPath1))
		assertTrue(ffs.exists(oldPath6))

		val node1 = repo.rawTree.find { it.id == 1 }
		val node6 = repo.rawTree.find { it.id == 6 }

		node1.value = node1.value.copy(order = 2)
		node6.value = node6.value.copy(order = 0)

		repo.rationalizeTree()

		assertFalse(ffs.exists(oldPath1))
		assertFalse(ffs.exists(oldPath6))

		val newPath1 = repo.getSceneFilePath(1).toOkioPath()
		val newPath6 = repo.getSceneFilePath(6).toOkioPath()

		assertTrue(ffs.exists(newPath1))
		assertTrue(ffs.exists(newPath6))

		assertNotEquals(oldPath1, newPath1)
		assertNotEquals(oldPath6, newPath6)
	}
}
