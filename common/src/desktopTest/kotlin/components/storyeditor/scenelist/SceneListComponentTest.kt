package components.storyeditor.scenelist

import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneListComponent
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.emptySceneSummary
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.nux.NuxFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.data.nux.NuxState
import com.darkrockstudios.apps.hammer.common.data.nux.NuxPlatform
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Source
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest
import utils.FixedRandom
import utils.TestComponentContext
import utils.inMemoryGlobalSettingsStore
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SceneListComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var nuxFileSystem: FakeFileSystem
	private lateinit var nuxRepository: NuxRepository
	private lateinit var selectedSceneFlow: MutableSharedFlow<SceneItem?>
	private val bufferCallback = slot<suspend (SceneBuffer) -> Unit>()
	private val sceneListCallback = slot<(SceneSummary) -> Unit>()

	private val selectedScenes = mutableListOf<SceneItem>()
	private val openedScenes = mutableListOf<SceneItem>()
	private var outlineShown = false
	private var readerShown = false

	@BeforeEach
	override fun setup() {
		super.setup()

		selectedScenes.clear()
		openedScenes.clear()
		outlineShown = false
		readerShown = false
		selectedSceneFlow = MutableSharedFlow(replay = 1)

		sceneEditor = mockk(relaxed = true)
		every { sceneEditor.getSceneSummaries() } returns emptySceneSummary(projectDef)
		every {
			sceneEditor.subscribeToBufferUpdates(any(), any(), capture(bufferCallback))
		} returns mockk(relaxed = true)
		every {
			sceneEditor.subscribeToSceneUpdates(any(), capture(sceneListCallback))
		} returns mockk(relaxed = true)

		globalSettingsStore = inMemoryGlobalSettingsStore()
		nuxFileSystem = FakeFileSystem()
		useNuxRepository()
	}

	/** Android by default so the menu tip is [NuxHint.SceneListSwipeMenu]; later visits lose their roll unless told otherwise. */
	private fun useNuxRepository(
		random: Random = FixedRandom.losing(),
		platform: NuxPlatform = NuxPlatform.Android,
		fileSystem: FileSystem = nuxFileSystem,
	) {
		nuxRepository = NuxRepository(
			NuxFilesystemDatasource(fileSystem, createTomlSerializer()),
			globalSettingsStore,
			platform = platform,
			random = random,
		)
		setupComponentKoin(componentModule())
	}

	private fun componentModule(defaultDispatcher: CoroutineContext? = null) = module {
		single { nuxRepository }
		scope<ProjectDefScope> {
			scoped { sceneEditor }
		}
		if (defaultDispatcher != null) single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { defaultDispatcher }
	}

	private fun storedNuxState() = NuxFilesystemDatasource(nuxFileSystem, createTomlSerializer()).load()

	private fun newComponent(componentContext: TestComponentContext = context) = SceneListComponent(
		componentContext = componentContext,
		projectDef = projectDef,
		selectedSceneItem = selectedSceneFlow,
		sceneSelected = { selectedScenes.add(it) },
		openSceneEditor = { openedScenes.add(it) },
		showOutlineOverviewDialog = { outlineShown = true },
		showReader = { readerShown = true },
	)

	private fun sceneItem(id: Int = 1, type: SceneItem.Type = SceneItem.Type.Scene) = SceneItem(
		projectDef = projectDef,
		type = type,
		id = id,
		name = "Scene $id",
		order = 0,
	)

	@Test
	fun `Scene summaries load on construction`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(emptySceneSummary(projectDef), comp.state.value.sceneSummary)
	}

	@Test
	fun `Externally selected scene is reflected in state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val scene = sceneItem(4)
		selectedSceneFlow.emit(scene)
		advanceUntilIdle()

		assertEquals(scene, comp.state.value.selectedSceneItem)
	}

	@Test
	fun `Selecting a scene notifies the parent and updates state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val scene = sceneItem(2)
		comp.onSceneSelected(scene)

		assertEquals(listOf(scene), selectedScenes)
		assertEquals(scene, comp.state.value.selectedSceneItem)
	}

	@Test
	fun `Creating a scene under the root passes a null parent and opens the new scene in the editor`() =
		runTest(mainTestDispatcher) {
			val root = sceneItem(0, SceneItem.Type.Root)
			val created = sceneItem(9)
			coEvery { sceneEditor.createScene(null, "New Scene") } returns created

			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.createScene(root, "New Scene")
			advanceUntilIdle()

			coVerify { sceneEditor.createScene(null, "New Scene") }
			assertEquals(listOf(created), openedScenes)
			assertTrue(selectedScenes.isEmpty())
		}

	@Test
	fun `Failed scene creation opens nothing`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.createScene(any(), any()) } returns null

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.createScene(null, "Nope")
		advanceUntilIdle()

		assertTrue(openedScenes.isEmpty())
	}

	@Test
	fun `Creating a group under a group keeps the parent`() = runTest(mainTestDispatcher) {
		val parent = sceneItem(3, SceneItem.Type.Group)
		coEvery { sceneEditor.createGroup(parent, "Part Two") } returns sceneItem(10, SceneItem.Type.Group)

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.createGroup(parent, "Part Two")
		advanceUntilIdle()

		coVerify { sceneEditor.createGroup(parent, "Part Two") }
	}

	@Test
	fun `Deleting routes scenes and groups to their own repository calls`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val scene = sceneItem(1, SceneItem.Type.Scene)
		comp.deleteScene(scene)
		coVerify { sceneEditor.deleteScene(scene) }

		val group = sceneItem(2, SceneItem.Type.Group)
		comp.deleteScene(group)
		coVerify { sceneEditor.deleteGroup(group) }
	}

	@Test
	fun `Deleting the root is refused`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFailsWith<IllegalStateException> {
			comp.deleteScene(sceneItem(0, SceneItem.Type.Root))
		}
	}

	@Test
	fun `Buffer updates mark and clear the dirty flag on the summary`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val scene = sceneItem(5)
		val dirtyBuffer = SceneBuffer(
			content = SceneContent(scene = scene, markdown = "text"),
			dirty = true,
			source = UpdateSource.Editor,
		)
		bufferCallback.captured(dirtyBuffer)
		advanceUntilIdle()
		assertTrue(comp.state.value.sceneSummary?.hasDirtyBuffer?.contains(5) == true)

		bufferCallback.captured(dirtyBuffer.copy(dirty = false))
		advanceUntilIdle()
		assertFalse(comp.state.value.sceneSummary?.hasDirtyBuffer?.contains(5) == true)
	}

	@Test
	fun `Archived scenes dialog shows and dismisses`() = runTest(mainTestDispatcher) {
		val archived = listOf(sceneItem(8).copy(archived = true))
		every { sceneEditor.getArchivedScenes() } returns archived

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showArchivedScenes()
		assertTrue(comp.state.value.showArchivedDialog)
		assertEquals(archived, comp.state.value.archivedScenes)

		comp.dismissArchivedDialog()
		assertFalse(comp.state.value.showArchivedDialog)
	}

	@Test
	fun `Unarchiving opens the restored scene in the editor and closes the dialog`() = runTest(mainTestDispatcher) {
		val archived = sceneItem(8).copy(archived = true)
		val restored = archived.copy(archived = false)
		coEvery { sceneEditor.unarchiveScene(archived) } returns restored

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showArchivedScenes()
		comp.unarchiveScene(archived)
		advanceUntilIdle()

		assertEquals(listOf(restored), openedScenes)
		assertFalse(comp.state.value.showArchivedDialog)
	}

	@Test
	fun `Failed unarchive leaves the dialog open`() = runTest(mainTestDispatcher) {
		val archived = sceneItem(8).copy(archived = true)
		coEvery { sceneEditor.unarchiveScene(archived) } returns null

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showArchivedScenes()
		comp.unarchiveScene(archived)
		advanceUntilIdle()

		assertTrue(openedScenes.isEmpty())
		assertTrue(comp.state.value.showArchivedDialog)
	}

	@Test
	fun `Scene list updates from the repository replace the summary`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val newSummary = SceneSummary(emptySceneSummary(projectDef).sceneTree, persistentSetOf(1))
		sceneListCallback.captured(newSummary)
		advanceUntilIdle()

		assertEquals(newSummary, comp.state.value.sceneSummary)
	}

	@Test
	fun `Outline overview request is forwarded`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showOutlineOverview()
		assertTrue(outlineShown)
	}

	@Test
	fun `Reader mode request is forwarded`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showReaderMode()
		assertTrue(readerShown)
	}

	@Test
	fun `Rename delegates to the repository`() = runTest(mainTestDispatcher) {
		val scene = sceneItem(1)
		coEvery { sceneEditor.renameScene(scene, "Renamed") } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.renameScene(scene, "Renamed"))
		coVerify { sceneEditor.renameScene(scene, "Renamed") }
	}

	/** A chapter group holding one scene, so both tips have a row to point at. */
	private fun populatedSummary(): SceneSummary {
		val root = TreeNode(sceneItem(0, SceneItem.Type.Root))
		val chapter = TreeNode(sceneItem(1, SceneItem.Type.Group))
		chapter.addChild(TreeNode(sceneItem(2)))
		root.addChild(chapter)
		val tree = Tree<SceneItem>().apply { setRoot(root) }
		return SceneSummary(tree.toImmutableTree(), persistentSetOf())
	}

	private val bothTips = listOf(NuxHint.SceneListSwipeMenu, NuxHint.SceneListReorder)

	private suspend fun unseen() = nuxRepository.unseen(bothTips).first()

	/** Every hint list the component publishes, from construction on. */
	private fun SceneListComponent.recordOffers(): List<List<NuxHint>> =
		mutableListOf<List<NuxHint>>().also { offers -> state.subscribe { offers += it.offeredHints } }

	@Test
	fun `A list with rows offers every unseen tip, swipe first`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(bothTips, comp.state.value.offeredHints)
	}

	@Test
	fun `An empty list offers no tip until it gains a row`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val whileEmpty = comp.state.value.offeredHints

		sceneListCallback.captured(populatedSummary())
		advanceUntilIdle()

		assertTrue(whileEmpty.isEmpty())
		assertEquals(bothTips, comp.state.value.offeredHints)
	}

	@Test
	fun `Dismissing a tip marks it seen and offers nothing more on this visit`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.dismissHint(NuxHint.SceneListSwipeMenu)
		advanceUntilIdle()

		assertTrue(comp.state.value.offeredHints.isEmpty())
		assertEquals(listOf(NuxHint.SceneListReorder), unseen())
	}

	@Test
	fun `Dismissing the reorder tip leaves the swipe tip unseen`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.dismissHint(NuxHint.SceneListReorder)
		advanceUntilIdle()

		assertTrue(comp.state.value.offeredHints.isEmpty())
		assertEquals(listOf(NuxHint.SceneListSwipeMenu), unseen())
	}

	/** Dismisses the swipe tip on a first visit, then opens the list again; returns the later visit. */
	private fun TestScope.visitAgainAfterDismissing(): Pair<SceneListComponent, TestComponentContext> {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val first = newComponent()
		context.resume()
		advanceUntilIdle()
		first.dismissHint(NuxHint.SceneListSwipeMenu)
		advanceUntilIdle()
		context.destroy()

		val laterContext = TestComponentContext.create()
		val later = newComponent(laterContext)
		laterContext.resume()
		advanceUntilIdle()
		return later to laterContext
	}

	@Test
	fun `A later visit offers the next tip when its roll wins`() = runTest(mainTestDispatcher) {
		useNuxRepository(random = FixedRandom.winning())

		val (later, laterContext) = visitAgainAfterDismissing()

		assertEquals(listOf(NuxHint.SceneListReorder), later.state.value.offeredHints)
		laterContext.destroy()
	}

	@Test
	fun `A later visit offers no tip when its roll loses`() = runTest(mainTestDispatcher) {
		useNuxRepository(random = FixedRandom.losing())

		val (later, laterContext) = visitAgainAfterDismissing()

		assertTrue(later.state.value.offeredHints.isEmpty())
		laterContext.destroy()
	}

	@Test
	fun `A later visit that lost its roll stays quiet when tips are turned off and on`() = runTest(mainTestDispatcher) {
		// One roll per visit: the first visit's, the later visit's losing one, then a win a re-roll would get.
		useNuxRepository(random = FixedRandom(FixedRandom.LOSE, FixedRandom.LOSE, FixedRandom.WIN))
		val (later, laterContext) = visitAgainAfterDismissing()

		globalSettingsStore.updateSettings { it.copy(tipsEnabled = false) }
		advanceUntilIdle()
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = true) }
		advanceUntilIdle()

		assertTrue(later.state.value.offeredHints.isEmpty())
		laterContext.destroy()
	}

	@Test
	fun `A reset during a later visit that lost its roll offers the tips again`() = runTest(mainTestDispatcher) {
		useNuxRepository(random = FixedRandom.losing())
		val (later, laterContext) = visitAgainAfterDismissing()

		nuxRepository.resetHints()
		advanceUntilIdle()

		assertEquals(bothTips, later.state.value.offeredHints)
		laterContext.destroy()
	}

	@Test
	fun `On desktop the list offers the right-click tip and never the swipe tip`() =
		runTest(mainTestDispatcher) {
			useNuxRepository(platform = NuxPlatform.Desktop)
			every { sceneEditor.getSceneSummaries() } returns populatedSummary()

			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			assertEquals(listOf(NuxHint.SceneListContextMenu, NuxHint.SceneListReorder), comp.state.value.offeredHints)
		}

	@Test
	fun `On iOS the list offers no menu tip, only reorder`() = runTest(mainTestDispatcher) {
		useNuxRepository(platform = NuxPlatform.Ios)
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf(NuxHint.SceneListReorder), comp.state.value.offeredHints)
	}

	@Test
	fun `A configuration change keeps the visit's losing roll`() = runTest(mainTestDispatcher) {
		val random = FixedRandom(FixedRandom.LOSE, FixedRandom.WIN)
		useNuxRepository(random = random)
		nuxRepository.markSeen(NuxHint.SceneListSwipeMenu)
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		newComponent()
		context.resume()
		advanceUntilIdle()

		val rotated = context.recreateForConfigurationChange()
		val afterRotation = newComponent(rotated)
		rotated.resume()
		advanceUntilIdle()

		assertTrue(afterRotation.state.value.offeredHints.isEmpty())
		assertEquals(1, random.rolls)
	}

	@Test
	fun `A configuration change keeps the visit's dismissal`() = runTest(mainTestDispatcher) {
		useNuxRepository(random = FixedRandom.winning())
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.dismissHint(NuxHint.SceneListSwipeMenu)
		advanceUntilIdle()

		val rotated = context.recreateForConfigurationChange()
		val afterRotation = newComponent(rotated)
		rotated.resume()
		advanceUntilIdle()

		assertTrue(afterRotation.state.value.offeredHints.isEmpty())
	}

	@Test
	fun `A dismissal survives the list closing straight after`() = runTest(mainTestDispatcher) {
		useNuxRepository(random = FixedRandom.winning())
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val first = newComponent()
		context.resume()
		advanceUntilIdle()

		first.dismissHint(NuxHint.SceneListSwipeMenu)
		context.destroy()
		val laterContext = TestComponentContext.create()
		val later = newComponent(laterContext)
		laterContext.resume()
		advanceUntilIdle()

		assertEquals(listOf(NuxHint.SceneListReorder), later.state.value.offeredHints)
		assertEquals(setOf(NuxHint.SceneListSwipeMenu.id), storedNuxState().seenHints)
		laterContext.destroy()
	}

	@Test
	fun `Once every tip is seen none is offered`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		nuxRepository.markSeen(NuxHint.SceneListSwipeMenu)
		nuxRepository.markSeen(NuxHint.SceneListReorder)

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.state.value.offeredHints.isEmpty())
	}

	@Test
	fun `A tip already seen is never offered while the seen list loads`() = runTest(mainTestDispatcher) {
		useNuxRepository(random = FixedRandom.winning())
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		NuxFilesystemDatasource(nuxFileSystem, createTomlSerializer())
			.store(NuxState(seenHints = setOf(NuxHint.SceneListSwipeMenu.id)))

		val comp = newComponent()
		val offers = comp.recordOffers()
		context.resume()
		advanceUntilIdle()

		assertTrue(offers.none { NuxHint.SceneListSwipeMenu in it })
		assertEquals(listOf(NuxHint.SceneListReorder), comp.state.value.offeredHints)
	}

	@Test
	fun `An unreadable tips file still lets the list open and offer tips`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		NuxFilesystemDatasource(nuxFileSystem, createTomlSerializer())
			.store(NuxState(seenHints = setOf(NuxHint.SceneListSwipeMenu.id)))
		val unreadable = object : ForwardingFileSystem(nuxFileSystem) {
			override fun source(file: Path): Source = throw IOException("read failed")
		}
		useNuxRepository(fileSystem = unreadable)

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(bothTips, comp.state.value.offeredHints)
	}

	@Test
	fun `A dismissed tip does not flash back from an update already under way`() = runTest(mainTestDispatcher) {
		// Recomputes run eagerly, so one can be waiting on the main thread when the dismissal lands.
		setupComponentKoin(componentModule(defaultDispatcher = UnconfinedTestDispatcher(testScheduler)))
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val offers = comp.recordOffers()

		nuxRepository.markSeen(NuxHint.SceneListReorder)
		comp.dismissHint(NuxHint.SceneListSwipeMenu)
		val dismissedAt = offers.size
		advanceUntilIdle()

		assertTrue(offers.drop(dismissedAt).all { it.isEmpty() }, "offers after dismissal: ${offers.drop(dismissedAt)}")
	}

	@Test
	fun `Turning tips off withdraws them without marking them seen`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		globalSettingsStore.updateSettings { it.copy(tipsEnabled = false) }
		advanceUntilIdle()
		val whileOff = comp.state.value.offeredHints
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = true) }
		advanceUntilIdle()

		assertTrue(whileOff.isEmpty())
		assertEquals(bothTips, comp.state.value.offeredHints)
	}

	@Test
	fun `Turning tips off from a tip switches the setting off and leaves the tips unseen`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.turnOffTips()
		advanceUntilIdle()

		assertTrue(comp.state.value.offeredHints.isEmpty())
		assertFalse(globalSettingsStore.globalSettings.tipsEnabled)
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = true) }
		assertEquals(bothTips, unseen())
	}

	@Test
	fun `Tips turned off from a tip come back to the same list when turned on again`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.turnOffTips()
		advanceUntilIdle()
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = true) }
		advanceUntilIdle()

		assertEquals(bothTips, comp.state.value.offeredHints)
	}

	@Test
	fun `A reset brings a dismissed tip back to the same list`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.dismissHint(NuxHint.SceneListSwipeMenu)
		advanceUntilIdle()

		nuxRepository.resetHints()
		advanceUntilIdle()

		assertEquals(bothTips, comp.state.value.offeredHints)
	}

	@Test
	fun `Tips turned back on do not bring a second tip after a dismissal`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneSummaries() } returns populatedSummary()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.dismissHint(NuxHint.SceneListSwipeMenu)
		advanceUntilIdle()

		globalSettingsStore.updateSettings { it.copy(tipsEnabled = false) }
		advanceUntilIdle()
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = true) }
		advanceUntilIdle()

		assertTrue(comp.state.value.offeredHints.isEmpty())
	}

	@Test
	fun `Dismissing a tip that is not offered marks nothing seen`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.dismissHint(NuxHint.SceneListSwipeMenu)
		sceneListCallback.captured(populatedSummary())
		advanceUntilIdle()

		assertEquals(bothTips, unseen())
		assertEquals(bothTips, comp.state.value.offeredHints)
	}
}
