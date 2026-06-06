package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.*
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.toApiType
import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Durable behavior suite written against the [SceneEditorService] facade. The facade API
 * is fixed from Phase A onward, so this suite must stay GREEN and UNCHANGED through the
 * later repository extractions (Phases B–D) — its continued green is the regression proof
 * that the carve preserved behavior. Tests are organized by command/observable and pin the
 * subtle behaviors called out in the plan's Behavioral Risk Register.
 */
class SceneEditorServiceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@MockK
	private lateinit var syncDataRepository: SyncDataRepository

	@MockK
	private lateinit var idAllocator: IdAllocator

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource
	private lateinit var statisticsRepository: StatisticsRepository
	private lateinit var referenceIndexRepository: ReferenceIndexRepository
	private lateinit var writingSessionTracker: WritingSessionTracker

	private lateinit var repo: SceneRepository
	private lateinit var sceneMetadataRepository: SceneMetadataRepository
	private lateinit var sceneContentRepository: SceneContentRepository

	private var nextId = 100

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()

		statisticsRepository = mockk(relaxed = true)
		referenceIndexRepository = mockk(relaxed = true)
		writingSessionTracker = mockk(relaxed = true)

		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns ProjectMetadata(
			info = Info(
				created = Instant.parse("2022-01-01T00:00:00.000Z"),
				dataVersion = 1,
			)
		)

		nextId = 100
		coEvery { idAllocator.claimNextId() } answers { nextId++ }
		coEvery { idAllocator.findNextId() } just Runs

		coEvery { syncDataRepository.isServerSynchronized() } returns false
		coEvery { syncDataRepository.isEntityDirty(any()) } returns false
		coEvery { syncDataRepository.markEntityAsDirty(any(), any()) } just Runs
		coEvery { syncDataRepository.recordIdDeletion(any()) } just Runs
	}

	private fun createService(projectDef: ProjectDef): SceneEditorService {
		sceneMetadataDatasource = SceneMetadataDatasource(ffs, toml, projectDef)
		sceneDatasource = SceneDatasource(projectDef, ffs)
		sceneMetadataRepository = SceneMetadataRepository(
			projectDef = projectDef,
			sceneMetadataDatasource = sceneMetadataDatasource,
			projectMetadataDatasource = projectMetadataDatasource,
			strRes = mockk {
				coEvery { get(any<StringResource>()) } returns "New Draft"
			},
			clock = Clock.System,
		)
		sceneContentRepository = SceneContentRepository(
			projectDef = projectDef,
			sceneDatasource = sceneDatasource,
		)
		repo = SceneRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idAllocator = idAllocator,
			sceneMetadataRepository = sceneMetadataRepository,
			sceneContentRepository = sceneContentRepository,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
			clock = Clock.System,
		)
		return SceneEditorService(
			sceneEditorRepository = repo,
			sceneContentRepository = sceneContentRepository,
			sceneMetadataRepository = sceneMetadataRepository,
			referenceIndexRepository = referenceIndexRepository,
			statisticsRepository = statisticsRepository,
			writingSessionTracker = writingSessionTracker,
		)
	}

	private suspend fun initializedService(): SceneEditorService {
		val projDef = getProject1Def()
		createProject(ffs, PROJECT_1_NAME)
		val service = createService(projDef)
		repo.initializeSceneEditor()
		return service
	}

	private fun sceneItem(id: Int, order: Int, type: SceneItem.Type = SceneItem.Type.Scene) =
		SceneItem(
			projectDef = getProject1Def(),
			type = type,
			id = id,
			name = if (type == SceneItem.Type.Scene) "Scene ID $id" else "Chapter ID $id",
			order = order,
		)

	// region Structure: create / delete / rename / move

	@Test
	fun `Create scene under root adds it to tree and filesystem and marks stats dirty`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()

			val created = service.createScene(null, "New Scene")
			assertNotNull(created)
			assertEquals("New Scene", created.name)
			assertEquals(SceneItem.Type.Scene, created.type)
			assertEquals(100, created.id)

			assertNotNull(service.getSceneItemFromId(created.id))
			assertTrue(ffs.exists(service.getSceneFilePathOrNull(created.id)!!.toOkioPath()))
			coVerify { statisticsRepository.markDirty() }
		}

	@Test
	fun `Create group under root adds a directory to the tree`() = runTest(mainTestDispatcher) {
		val service = initializedService()

		val created = service.createGroup(null, "New Group")
		assertNotNull(created)
		assertEquals(SceneItem.Type.Group, created.type)

		val path = service.getSceneFilePathOrNull(created.id)!!.toOkioPath()
		assertTrue(ffs.exists(path))
		assertTrue(ffs.metadata(path).isDirectory)
	}

	@Test
	fun `Delete scene removes it from the tree and fires delete side-effects`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val scene = service.getSceneItemFromId(1)!!

			val deleted = service.deleteScene(scene)
			assertTrue(deleted)
			assertNull(service.getSceneItemFromId(1))

			coVerify { statisticsRepository.markDirty() }
			coVerify { referenceIndexRepository.markSceneDeleted(1) }
			coVerify { writingSessionTracker.forgetBaseline(1) }
		}

	@Test
	fun `Delete group removes an empty group from the tree`() = runTest(mainTestDispatcher) {
		val service = initializedService()
		// deleteGroup only succeeds on an empty group, so create a fresh one.
		val group = service.createGroup(null, "Empty Group")!!
		assertEquals(SceneItem.Type.Group, group.type)

		val deleted = service.deleteGroup(group)
		assertTrue(deleted)
		assertNull(service.getSceneItemFromId(group.id))
	}

	@Test
	fun `Rename scene renames the file on disk and updates the tree`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val scene = service.getSceneItemFromId(1)!!
			val oldPath = service.getSceneFilePathOrNull(1)!!.toOkioPath()

			val renamed = service.renameScene(scene, "Renamed Scene")
			assertTrue(renamed)

			assertEquals("Renamed Scene", service.getSceneItemFromId(1)!!.name)
			assertFalse(ffs.exists(oldPath))
			val newPath = service.getSceneFilePathOrNull(1)!!.toOkioPath()
			assertTrue(ffs.exists(newPath))
			assertTrue(newPath.name.contains("Renamed Scene"))
		}

	@Test
	fun `Move scene within root reorders siblings`() = runTest(mainTestDispatcher) {
		val service = initializedService()
		// Initial root order: 1, 2, 6, 7
		val moveRequest = MoveRequest(
			id = 6,
			toPosition = InsertPosition(
				coords = NodeCoordinates(parentIndex = 0, childLocalIndex = 0, globalIndex = 1),
				before = false,
			),
		)
		service.moveScene(moveRequest)

		val rootChildren = service.getSceneTree().root.children.map { it.value.id }
		assertEquals(listOf(1, 6, 2, 7), rootChildren)
	}

	// endregion

	// region Archive

	@Test
	fun `Archive scene removes it from the tree but keeps it findable including archived`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val scene = service.getSceneItemFromId(1)!!

			val archived = service.archiveScene(scene)
			assertTrue(archived)

			assertNull(service.getSceneItemFromId(1))
			assertNotNull(service.getSceneItemFromIdIncludingArchived(1))
			assertTrue(service.getArchivedScenes().any { it.id == 1 })
		}

	@Test
	fun `Unarchive scene restores it to the tree`() = runTest(mainTestDispatcher) {
		val service = initializedService()
		service.archiveScene(service.getSceneItemFromId(1)!!)

		val archivedScene = service.getArchivedScenes().first { it.id == 1 }
		val unarchived = service.unarchiveScene(archivedScene)
		assertNotNull(unarchived)
		assertFalse(unarchived.archived)
		assertNotNull(service.getSceneItemFromId(1))
	}

	// endregion

	// region Buffers / content

	@Test
	fun `Edited content propagates to buffer subscribers`() = runTest(mainTestDispatcher) {
		val service = initializedService()

		val content = SceneContent(scene = sceneItem(1, 0), markdown = "New Content!!")

		val captured = slot<SceneBuffer>()
		val onBufferUpdate: suspend (SceneBuffer) -> Unit = mockk()
		coEvery { onBufferUpdate(capture(captured)) } just Runs

		val job = service.subscribeToBufferUpdates(null, scope, onBufferUpdate)
		service.onContentChanged(content, UpdateSource.Editor)
		advanceUntilIdle()
		job.cancelAndJoin()

		coVerify(exactly = 1) { onBufferUpdate(any()) }
		assertEquals("New Content!!", captured.captured.content.markdown)
		assertTrue(captured.captured.dirty)
	}

	@Test
	fun `Load scene buffer returns content and remembers a writing-session baseline`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val scene = service.getSceneItemFromId(3)!!

			val buffer = service.loadSceneBuffer(scene)
			assertEquals(scene, buffer.content.scene)
			assertEquals("Content of scene id 3", buffer.content.markdown)
			coVerify { writingSessionTracker.rememberBaseline(3, "Content of scene id 3") }
		}

	@Test
	fun `Store scene buffer persists content and fires save side-effects`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val scene = service.getSceneItemFromId(3)!!

			service.loadSceneBuffer(scene)
			service.onContentChanged(SceneContent(scene, "Edited body"), UpdateSource.Editor)
			advanceUntilIdle()

			val stored = service.storeSceneBuffer(scene)
			assertTrue(stored)

			val path = service.getSceneFilePathOrNull(3)!!.toOkioPath()
			ffs.read(path) { assertEquals("Edited body", readUtf8()) }

			assertFalse(service.hasDirtyBuffers())
			coVerify { statisticsRepository.markDirty() }
			coVerify { writingSessionTracker.onSceneSaved(eq(3), any(), any()) }
		}

	@Test
	fun `Store-all-buffers flushes every dirty buffer`() = runTest(mainTestDispatcher) {
		val service = initializedService()

		service.onContentChanged(SceneContent(sceneItem(1, 0), "Body 1"), UpdateSource.Editor)
		service.onContentChanged(SceneContent(sceneItem(3, 0), "Body 3"), UpdateSource.Editor)
		advanceUntilIdle()
		assertTrue(service.hasDirtyBuffers())

		service.storeAllBuffers()
		assertFalse(service.hasDirtyBuffers())
	}

	@Test
	fun `Discard scene buffer reverts to on-disk content`() = runTest(mainTestDispatcher) {
		val service = initializedService()
		val scene = service.getSceneItemFromId(3)!!

		service.loadSceneBuffer(scene)
		service.onContentChanged(SceneContent(scene, "Unsaved edit"), UpdateSource.Editor)
		advanceUntilIdle()
		assertTrue(service.hasDirtyBuffers())

		service.discardSceneBuffer(scene)
		val buffer = service.getSceneBuffer(scene)
		assertNotNull(buffer)
		assertFalse(buffer.dirty)
		assertEquals("Content of scene id 3", buffer.content.markdown)
	}

	// endregion

	// region Metadata

	@Test
	fun `Load scene metadata falls back to the default draft name when none stored`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			// Scene 7 has no metadata in the fixture.
			val metadata = service.loadSceneMetadata(7)
			assertEquals(SceneMetadata(currentDraftName = "New Draft"), metadata)
		}

	@Test
	fun `Store metadata persists, emits an update, and applies the reference delta`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val sceneId = 1
			val initial = service.loadSceneMetadata(sceneId)
			val updated = initial.copy(
				notes = "Updated notes",
				confirmedReferences = setOf(7, 9),
			)

			service.storeMetadata(updated, sceneId)

			assertEquals(updated, service.loadSceneMetadata(sceneId))
			coVerify {
				referenceIndexRepository.applySceneDelta(
					sceneId = sceneId,
					added = setOf(7, 9),
					removed = emptySet(),
				)
			}
		}

	@Test
	fun `Store metadata emits on the metadata update flow`() = runTest(mainTestDispatcher) {
		val service = initializedService()
		val sceneId = 1
		val updated = service.loadSceneMetadata(sceneId).copy(notes = "flow notes")

		// metadataUpdateFlow has no replay, so subscribe before storing.
		val received = mutableListOf<Pair<Int, SceneMetadata>>()
		val job = scope.launch { service.metadataUpdateFlow.collect { received.add(it) } }
		advanceUntilIdle()

		service.storeMetadata(updated, sceneId)
		advanceUntilIdle()
		job.cancelAndJoin()

		assertTrue(received.any { it.first == sceneId && it.second.notes == "flow notes" })
	}

	// endregion

	// region Derived state — scene list

	@Test
	fun `Subscribing to scene updates triggers an initial emission`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()

			val onUpdate: (SceneSummary) -> Unit = mockk(relaxed = true)
			val job = service.subscribeToSceneUpdates(scope, onUpdate)
			advanceUntilIdle()
			job.cancelAndJoin()

			verify(atLeast = 1) { onUpdate(any()) }
		}

	// Risk Register #7: the scene-list SharedFlow replays its latest value to late subscribers.
	@Test
	fun `Scene list channel replays the latest summary to a late subscriber`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			advanceUntilIdle()

			// A fresh collector must receive the replayed snapshot without any new mutation.
			val summary = service.sceneListChannel.first()
			assertTrue(summary.sceneTree.totalChildren > 0)
		}

	// Risk Register #5: SceneSummary composes the tree with the set of dirty buffer ids.
	@Test
	fun `Scene summaries combine the tree with dirty buffer ids`() = runTest(mainTestDispatcher) {
		val service = initializedService()

		service.onContentChanged(SceneContent(sceneItem(1, 0), "dirty"), UpdateSource.Editor)
		advanceUntilIdle()

		val summary = service.getSceneSummaries()
		assertTrue(summary.hasDirtyBuffer.contains(1))
		assertTrue(summary.sceneTree.totalChildren > 0)
	}

	// endregion

	// region Reads / paths

	@Test
	fun `Path segments are empty for an archived scene`() = runTest(mainTestDispatcher) {
		val service = initializedService()
		service.archiveScene(service.getSceneItemFromId(1)!!)

		val archived = service.getArchivedScenes().first { it.id == 1 }
		assertTrue(service.getPathSegments(archived).isEmpty())
		// And it is still resolvable from the filesystem via the archived-aware lookup.
		assertNotNull(service.resolveScenePathFromFilesystemIncludingArchived(1))
	}

	@Test
	fun `Resolve scene path from filesystem returns the padded on-disk path`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val path = service.resolveScenePathFromFilesystem(3)?.toOkioPath()
			assertNotNull(path)

			// After initialize, names are migrated to the v2 `~` delimiter.
			val segments = path.segments.reversed()
			assertEquals("0~Scene ID 3~3.md", segments[0])
			assertEquals("1~Chapter ID 2~2", segments[1])
		}

	// Note: the blank-name negative case is intentionally omitted — a sibling test class
	// mockkObject's ProjectsRepository.Companion without unmocking, polluting the static
	// validateFileName globally. Here we only assert the facade forwards a valid name.
	@Test
	fun `Validate scene name delegates and accepts a valid name`() = runTest(mainTestDispatcher) {
		val service = initializedService()
		assertTrue(service.validateSceneName("A good name").isSuccess)
	}

	// endregion

	// region Risk Register

	// Risk Register #1: when the sibling count crosses a digit boundary the order zero-padding
	// must widen and existing sibling filenames must be re-padded to match.
	@Test
	fun `Creating scenes across a digit boundary re-pads sibling filenames`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			// Root starts with 4 children (orders 0..3). Keep creating until an order reaches 10,
			// which forces single->double digit padding across all root siblings.
			repeat(8) { service.createScene(null, "Filler $it") }

			// Scene 1 (order 0) must now be padded to two digits on disk (v2 `~` delimiter).
			val scene1Name = service.getSceneFilePathOrNull(1)!!.toOkioPath().name
			assertTrue(
				scene1Name.startsWith("00~"),
				"Expected re-padded two-digit order, was '$scene1Name'",
			)
		}

	// Risk Register #4: markForSynchronization must hash the exact persisted identity. Pin the
	// EntityHasher inputs so a future carve can't silently change what gets hashed.
	@Test
	fun `Marking for synchronization hashes the exact persisted scene identity`() =
		runTest(mainTestDispatcher) {
			coEvery { syncDataRepository.isServerSynchronized() } returns true
			coEvery { syncDataRepository.isEntityDirty(1) } returns false

			val service = initializedService()
			val scene = service.getSceneItemFromId(1)!!
			val metadata = sceneMetadataDatasource.loadMetadata(1)

			// Recompute the expected hash from the pre-mutation persisted state.
			val expectedHash = EntityHasher.hashScene(
				id = scene.id,
				order = scene.order,
				path = service.getPathSegments(scene),
				name = scene.name,
				type = scene.type.toApiType(),
				content = service.loadSceneMarkdownRaw(scene),
				outline = metadata?.outline ?: "",
				notes = metadata?.notes ?: "",
				archived = scene.archived,
				confirmedReferences = metadata?.confirmedReferences ?: emptySet(),
				dismissedReferences = metadata?.dismissedReferences ?: emptySet(),
				tags = metadata?.tags ?: emptySet(),
				created = metadata?.created,
				lastEdited = metadata?.lastEdited,
			)

			val hashSlot = slot<String>()
			coEvery { syncDataRepository.markEntityAsDirty(1, capture(hashSlot)) } just Runs

			// Any mutation marks the scene for sync before changing it.
			service.renameScene(scene, "Renamed")

			coVerify { syncDataRepository.markEntityAsDirty(1, any()) }
			assertEquals(expectedHash, hashSlot.captured)
		}

	// Risk Register #3: editor edits are debounced by BUFFER_COOL_DOWN then autosaved to a
	// temp buffer. Verify the temp buffer is written once the cool-down elapses.
	@Test
	fun `Editor edits autosave to a temp buffer after the cool-down`() =
		runTest(mainTestDispatcher) {
			val service = initializedService()
			val scene = service.getSceneItemFromId(3)!!
			service.loadSceneBuffer(scene)

			service.onContentChanged(SceneContent(scene, "Autosaved body"), UpdateSource.Editor)
			advanceTimeBy(SceneContentRepository.BUFFER_COOL_DOWN.inWholeMilliseconds + 100)
			advanceUntilIdle()

			val tempPath = sceneDatasource.getSceneBufferDirectory().toOkioPath() / "3.md"
			assertTrue(ffs.exists(tempPath), "Expected temp buffer at $tempPath")
			ffs.read(tempPath) { assertEquals("Autosaved body", readUtf8()) }
		}

	// endregion
}
