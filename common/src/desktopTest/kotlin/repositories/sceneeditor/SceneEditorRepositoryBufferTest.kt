package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneEditorRepositoryBufferTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@MockK
	private lateinit var projectSynchronizer: ClientProjectSynchronizer

	@MockK
	private lateinit var idRepository: IdRepository

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		projectMetadataDatasource = ProjectMetadataDatasource(ffs, toml)
		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()

		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns mockk(relaxed = true)
		coEvery { projectSynchronizer.isServerSynchronized() } returns false
		coEvery { projectSynchronizer.isEntityDirty(any()) } returns false
		coEvery { projectSynchronizer.markEntityAsDirty(any(), any()) } just Runs
	}

	private fun createDatasource(projectDef: ProjectDef): SceneMetadataDatasource {
		return SceneMetadataDatasource(ffs, toml, projectDef)
	}

	private fun createSceneDatasource(projectDef: ProjectDef): SceneDatasource {
		return SceneDatasource(projectDef, ffs)
	}

	private fun createRepository(projectDef: ProjectDef): SceneEditorRepository {
		sceneMetadataDatasource = createDatasource(projectDef)
		sceneDatasource = createSceneDatasource(projectDef)
		return SceneEditorRepository(
			projectDef = projectDef,
			projectSynchronizer = projectSynchronizer,
			idRepository = idRepository,
			projectMetadataDatasource = projectMetadataDatasource,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
		)
	}

	@Test
	fun `Subscribe to Buffer Updates`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val newContent = SceneContent(
			scene = SceneItem(
				projectDef = getProject1Def(),
				type = SceneItem.Type.Scene,
				id = 1,
				name = "Scene ID 1",
				order = 0
			),
			markdown = "New Content!!"
		)

		val onBufferUpdate: (suspend (SceneBuffer) -> Unit) = mockk()
		val sceneBufferSlot = slot<SceneBuffer>()
		coEvery { onBufferUpdate(capture(sceneBufferSlot)) } just Runs

		val subJob = repo.subscribeToBufferUpdates(null, scope, onBufferUpdate)

		repo.onContentChanged(newContent, UpdateSource.Editor)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(exactly = 1) { onBufferUpdate(any()) }

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

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem2 = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)

		val newContent = SceneContent(
			scene = SceneItem(
				projectDef = getProject1Def(),
				type = SceneItem.Type.Scene,
				id = 1,
				name = "Scene ID 1",
				order = 0
			),
			markdown = "New Content!!"
		)

		val onBufferUpdate: (suspend (SceneBuffer) -> Unit) = mockk()
		coEvery { onBufferUpdate(any()) } just Runs

		val subJob = repo.subscribeToBufferUpdates(sceneItem2, scope, onBufferUpdate)
		repo.onContentChanged(newContent, UpdateSource.Editor)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(exactly = 0) { onBufferUpdate(any()) }
	}

	@Test
	fun `Subscribe to Scene Updates`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val onSceneUpdate: ((SceneSummary) -> Unit) = mockk()
		coEvery { onSceneUpdate(any()) } just Runs

		val subJob = repo.subscribeToSceneUpdates(scope, onSceneUpdate)
		advanceUntilIdle()
		subJob.cancelAndJoin()
		coVerify(exactly = 1) { onSceneUpdate(any()) }
	}

	@Test
	fun `Store Scene Buffer when no buffer is loaded`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)

		val stored = repo.storeSceneBuffer(sceneItem)
		assertFalse(stored)
	}

	@Test
	fun `Store Scene Buffer raw`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)
		val content = SceneContent(
			scene = sceneItem,
			markdown = "Updated scene content ID 3"
		)

		val stored = repo.storeSceneMarkdownRaw(content)
		assertTrue(stored)

		val scene3Path = repo.getSceneFilePath(3).toOkioPath()
		ffs.read(scene3Path) {
			val scene2Content = readUtf8()
			assertEquals(content.markdown, scene2Content)
		}
	}

	@Test
	fun `Get scene path from filesystem`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val sceneItem = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)

		val repo = createRepository(projDef)
		val scene3Path = repo.resolveScenePathFromFilesystem(sceneItem.id)?.toOkioPath()
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

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val sceneItem = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = 3,
			name = "Scene ID 3",
			order = 0
		)

		val buffer = repo.loadSceneBuffer(sceneItem)
		assertEquals(sceneItem, buffer.content.scene)
		assertEquals("Content of scene id 3", buffer.content.markdown)

		val stored = repo.storeSceneBuffer(sceneItem)
		assertTrue(stored)
	}

	@Test
	fun `No dirty buffers on first load`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		assertFalse(repo.hasDirtyBuffers())
	}

	private fun getTempBufferPath(sceneId: Int): Path {
		val bufferDir = sceneDatasource.getSceneBufferDirectory().toOkioPath()
		ffs.createDirectories(bufferDir)

		val tempBufPath = bufferDir / "$sceneId.md"
		return tempBufPath
	}

	private fun content(sceneId: Int) = "This is _test_ temp buffer content for Scene $sceneId"

	private fun writeTempBuffer(repo: SceneEditorRepository, sceneId: Int) {
		val tempContent = content(sceneId)
		val tempBufPath = getTempBufferPath(sceneId)
		ffs.write(tempBufPath) {
			writeUtf8(tempContent)
		}
	}

	@Test
	fun `Dirty buffer on first load because of temp buffer`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		writeTempBuffer(repo, 1)

		repo.initializeSceneEditor()

		assertTrue(repo.hasDirtyBuffers())

		assertTrue(repo.hasDirtyBuffer(1))
		assertFalse(repo.hasDirtyBuffer(2))
	}

	@Test
	fun `Discard Dirty buffer`() = runTest(mainTestDispatcher) {
		val sceneId = 1
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		writeTempBuffer(repo, sceneId)

		repo.initializeSceneEditor()
		assertTrue(repo.hasDirtyBuffer(sceneId))

		val sceneItem = SceneItem(
			projectDef = getProject1Def(),
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Scene ID $sceneId",
			order = 0
		)
		repo.discardSceneBuffer(sceneItem)

		assertFalse(repo.hasDirtyBuffer(1))

		val temp2Path = getTempBufferPath(1)
		assertFalse(ffs.exists(temp2Path))
	}

	@Test
	fun `Store all dirty buffers`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		writeTempBuffer(repo, 1)
		writeTempBuffer(repo, 3)

		repo.initializeSceneEditor()

		assertTrue(repo.hasDirtyBuffers())
		assertTrue(repo.hasDirtyBuffer(1))
		assertTrue(repo.hasDirtyBuffer(3))

		repo.storeAllBuffers()

		val temp1Path = getTempBufferPath(1)
		assertFalse(ffs.exists(temp1Path))

		val temp2Path = getTempBufferPath(3)
		assertFalse(ffs.exists(temp2Path))

		assertFalse(repo.hasDirtyBuffers())
		assertFalse(repo.hasDirtyBuffer(1))
		assertFalse(repo.hasDirtyBuffer(3))

		val scene1Path = repo.getSceneFilePath(1).toOkioPath()
		ffs.read(scene1Path) {
			val scene1Content = readUtf8()
			assertEquals(content(1), scene1Content)
		}

		val scene2Path = repo.getSceneFilePath(3).toOkioPath()
		ffs.read(scene2Path) {
			val scene2Content = readUtf8()
			assertEquals(content(3), scene2Content)
		}
	}

	@Test
	fun `Resolve path for scene ID`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		val path = repo.resolveScenePathFromFilesystem(3)?.toOkioPath()
		assertNotNull(path)

		val pathSegments = path.segments.reversed()
		assertEquals("0-Scene ID 3-3.md", pathSegments[0])
		assertEquals("1-Chapter ID 2-2", pathSegments[1])
		assertEquals("scenes", pathSegments[2])
	}

	@Test
	fun `Export Story`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
		repo.initializeSceneEditor()

		val exportPath = ffs.workingDirectory
		val path = repo.exportStory(exportPath.toHPath())

		ffs.read(path.toOkioPath()) {
			val exported = readUtf8()
			println("\"$exported\"")
			assertEquals(exportedStory1.trim(), exported.trim())
		}
	}

	private val exportedStory1 = """
		# Test Project 1


		## 1. Scene ID 1
		
		Content of scene id 1
		
		## 2. Chapter ID 2
		
		Content of scene id 3
		Content of scene id 4
		Content of scene id 5
		
		## 3. Scene ID 6
		
		Content of scene id 6
		
		## 4. Scene ID 7
		
		Content of scene id 7
	""".trimIndent()

	@Test
	fun `Get all scenes`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)

		val repo = createRepository(projDef)
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

		val repo = createRepository(projDef)
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