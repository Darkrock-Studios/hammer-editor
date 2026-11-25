package components.storyeditor

import com.arkivanov.decompose.value.getValue
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.storyeditor.DetailsRouter
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditor
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditorComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.readermode.ReaderMode
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditorComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.nux.NuxFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.data.references.AutoConfirmReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryEditorComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService
	private lateinit var settingsStore: GlobalSettingsStore

	private val sceneTreeCallbacks = mutableListOf<(SceneSummary) -> Unit>()
	private lateinit var sceneUpdates: MutableSharedFlow<SceneSummary>
	private val registry = ActionRegistry()
	private var focusModeScene: SceneItem? = null

	@BeforeEach
	override fun setup() {
		super.setup()

		sceneTreeCallbacks.clear()
		focusModeScene = null

		settingsStore = mockk(relaxed = true)
		every { settingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { settingsStore.globalSettingsUpdates } returns MutableSharedFlow()

		sceneEditor = mockk(relaxed = true)
		every { sceneEditor.subscribeToSceneUpdates(any(), capture(sceneTreeCallbacks)) } returns mockk(relaxed = true)
		sceneUpdates = MutableSharedFlow(replay = 1)
		every { sceneEditor.sceneListChannel } returns sceneUpdates

		val spellCheckRepository = mockk<SpellCheckRepository>(relaxed = true)
		every { spellCheckRepository.dictionaryFlow } returns MutableSharedFlow()

		val encyclopediaService = mockk<EncyclopediaService>(relaxed = true)
		every { encyclopediaService.entryListFlow } returns MutableSharedFlow()

		setupComponentKoin(module {
			single<CoroutineScope>(named(APP_SCOPE)) { scope }
			single { settingsStore }
			single { NuxRepository(NuxFilesystemDatasource(FakeFileSystem(), createTomlSerializer()), get()) }
			single { spellCheckRepository }
			scope<ProjectDefScope> {
				scoped { sceneEditor }
				scoped<SceneDraftRepository> { mockk(relaxed = true) }
				scoped<AutoConfirmReferencesUseCase> { mockk(relaxed = true) }
				scoped<ScrubInvalidReferencesUseCase> { mockk(relaxed = true) }
				scoped { encyclopediaService }
				scoped<TimelineService> { mockk { every { placementsFlow(any(), any()) } returns emptyFlow() } }
			}
		})
	}

	private fun newComponent(componentContext: TestComponentContext = context) = StoryEditorComponent(
		componentContext = componentContext,
		projectDef = projectDef,
		actions = registry,
		showFocusMode = { focusModeScene = it },
		showEntry = { },
		showGlobalSearchForTag = { },
		showTimelineEvent = { },
	)

	private fun sceneItem(id: Int = 1) = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = id,
		name = "Scene $id",
		order = 0,
	)

	private fun treeOf(vararg scenes: SceneItem): ImmutableTree<SceneItem> {
		val nodes = scenes.mapIndexed { i, scene ->
			TreeValue(value = scene, index = i + 1, parent = 0, children = persistentListOf(), depth = 1, totalChildren = 0)
		}
		val root = TreeValue(
			value = SceneItem(projectDef, SceneItem.Type.Root, id = 0, name = "root", order = 0),
			index = 0, parent = -1, children = persistentListOf(*nodes.toTypedArray()), depth = 0,
			totalChildren = nodes.size,
		)
		return ImmutableTree(root = root, totalChildren = nodes.size)
	}

	@Test
	fun `Initially shows the scene list with no detail`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertIs<StoryEditor.ChildDestination.List.Scenes>(comp.listRouterState.value.active.instance)
		assertIs<StoryEditor.ChildDestination.Detail.None>(comp.detailsRouterState.value.active.instance)
		assertFalse(comp.isDetailShown())
		assertTrue(comp.isAtRoot())
	}

	@Test
	fun `Showing a scene opens the detail and hides the list in single pane`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showScene(sceneItem(1))
		advanceUntilIdle()

		assertTrue(comp.isDetailShown())
		assertIs<StoryEditor.ChildDestination.Detail.EditorDestination>(comp.detailsRouterState.value.active.instance)
		assertIs<StoryEditor.ChildDestination.List.None>(comp.listRouterState.value.active.instance)
		assertFalse(comp.shouldCloseRoot.first())
		assertFalse(comp.isAtRoot())
	}

	@Test
	fun `Showing a scene in multi pane keeps the list visible`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.setMultiPane(true)
		comp.showScene(sceneItem(1))
		advanceUntilIdle()

		assertTrue(comp.state.value.isMultiPane)
		assertTrue(comp.isDetailShown())
		assertIs<StoryEditor.ChildDestination.List.Scenes>(comp.listRouterState.value.active.instance)
	}

	@Test
	fun `Switching from multi pane to single pane hides the list while a detail is shown`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.setMultiPane(true)
			comp.showScene(sceneItem(1))
			advanceUntilIdle()

			comp.setMultiPane(false)
			advanceUntilIdle()

			assertIs<StoryEditor.ChildDestination.List.None>(comp.listRouterState.value.active.instance)
			assertTrue(comp.isDetailShown())
		}

	@Test
	fun `Back closes the scene detail and shows the list again`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showScene(sceneItem(1))
		advanceUntilIdle()

		comp.onBack()
		advanceUntilIdle()

		assertFalse(comp.isDetailShown())
		assertIs<StoryEditor.ChildDestination.List.Scenes>(comp.listRouterState.value.active.instance)
		assertTrue(comp.shouldCloseRoot.first())
	}

	@Test
	fun `closeDetails is a no-op without an open detail`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(comp.closeDetails())
	}

	@Test
	fun `Deleting the shown scene from the tree closes the detail`() = runTest(mainTestDispatcher) {
		val shown = sceneItem(1)
		val other = sceneItem(2)

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showScene(shown)
		advanceUntilIdle()
		assertTrue(comp.isDetailShown())

		sceneTreeCallbacks.forEach { it(SceneSummary(treeOf(other), persistentSetOf())) }
		advanceUntilIdle()

		assertFalse(comp.isDetailShown())
	}

	@Test
	fun `Tree updates that retain the shown scene keep the detail open`() = runTest(mainTestDispatcher) {
		val shown = sceneItem(1)

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showScene(shown)
		advanceUntilIdle()

		sceneTreeCallbacks.forEach { it(SceneSummary(treeOf(shown), persistentSetOf())) }
		advanceUntilIdle()

		assertTrue(comp.isDetailShown())
	}

	@Test
	fun `Outline overview dialog opens and dismisses`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showOutlineOverview()
		advanceUntilIdle()
		assertIs<StoryEditor.ChildDestination.DialogDestination.OutlineDestination>(
			comp.dialogState.value.child?.instance
		)

		comp.dismissDialog()
		advanceUntilIdle()
		assertIs<StoryEditor.ChildDestination.DialogDestination.None>(comp.dialogState.value.child?.instance)
	}

	@Test
	fun `View drafts menu action pushes the drafts list and back returns to the editor`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.showScene(sceneItem(1))
			advanceUntilIdle()

			val editor = assertIs<StoryEditor.ChildDestination.Detail.EditorDestination>(
				comp.detailsRouterState.value.active.instance
			).component
			val viewDrafts = editor.state.value.menuItems
				.first { it.id == SceneEditorComponent.VIEW_DRAFTS_MENU_ITEM_ID }
			viewDrafts.action("")
			advanceUntilIdle()

			assertIs<StoryEditor.ChildDestination.Detail.DraftsDestination>(
				comp.detailsRouterState.value.active.instance
			)
			assertFalse(comp.isAtRoot())

			comp.onBack()
			advanceUntilIdle()
			assertIs<StoryEditor.ChildDestination.Detail.EditorDestination>(
				comp.detailsRouterState.value.active.instance
			)
		}

	private fun StoryEditor.reader(): ReaderMode =
		assertIs<StoryEditor.ChildDestination.Detail.ReaderDestination>(detailsRouterState.value.active.instance)
			.component

	private fun StoryEditor.sceneList() =
		assertIs<StoryEditor.ChildDestination.List.Scenes>(listRouterState.value.active.instance).component

	@Test
	fun `Showing the reader opens it in the detail and hides the list in single pane`() =
		runTest(mainTestDispatcher) {
			every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1), sceneItem(2))

			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.showReader(null)
			advanceUntilIdle()

			assertEquals(1, comp.reader().state.value.activeChapterId)
			assertIs<StoryEditor.ChildDestination.List.None>(comp.listRouterState.value.active.instance)
			assertFalse(comp.isAtRoot())
		}

	@Test
	fun `Showing the reader in multi pane keeps the list visible`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1), sceneItem(2))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.setMultiPane(true)
		comp.showReader(sceneItem(2))
		advanceUntilIdle()

		assertEquals(2, comp.reader().state.value.activeChapterId)
		assertIs<StoryEditor.ChildDestination.List.Scenes>(comp.listRouterState.value.active.instance)
	}

	@Test
	fun `Tapping a scene in the list while reading moves the reader instead of opening the editor`() =
		runTest(mainTestDispatcher) {
			every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1), sceneItem(2))

			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.setMultiPane(true)
			comp.showReader(null)
			advanceUntilIdle()
			val reader = comp.reader()

			comp.sceneList().onSceneSelected(sceneItem(2))
			advanceUntilIdle()

			assertSame(reader, comp.reader())
			assertEquals(2, reader.state.value.activeChapterId)
			assertEquals(2, reader.state.value.scrollToSceneId)
		}

	@Test
	fun `Showing a scene while reading opens the editor`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1), sceneItem(2))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showReader(null)
		advanceUntilIdle()
		comp.showScene(sceneItem(2))
		advanceUntilIdle()

		assertIs<StoryEditor.ChildDestination.Detail.EditorDestination>(comp.detailsRouterState.value.active.instance)

		comp.onBack()
		advanceUntilIdle()
		assertFalse(comp.isDetailShown())
	}

	@Test
	fun `The reader's edit action opens the editor for the scene being read`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1), sceneItem(2))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showReader(sceneItem(2))
		advanceUntilIdle()
		comp.reader().editScene()
		advanceUntilIdle()

		val editor =
			assertIs<StoryEditor.ChildDestination.Detail.EditorDestination>(comp.detailsRouterState.value.active.instance)
		assertEquals(2, editor.component.state.value.sceneItem.id)
	}

	@Test
	fun `Back from the reader shows the list again in single pane`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showReader(null)
		advanceUntilIdle()
		comp.onBack()
		advanceUntilIdle()

		assertFalse(comp.isDetailShown())
		assertIs<StoryEditor.ChildDestination.List.Scenes>(comp.listRouterState.value.active.instance)
	}

	@Test
	fun `Tree updates do not close the reader`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1), sceneItem(2))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showReader(sceneItem(1))
		advanceUntilIdle()

		val updated = SceneSummary(treeOf(sceneItem(2)), persistentSetOf())
		sceneTreeCallbacks.toList().forEach { it(updated) }
		sceneUpdates.emit(updated)
		advanceUntilIdle()

		assertEquals(2, comp.reader().state.value.activeChapterId)
	}

	@Test
	fun `The reader survives process death`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns treeOf(sceneItem(1), sceneItem(2))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showReader(null)
		advanceUntilIdle()
		comp.reader().showChapter(2)
		advanceUntilIdle()

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(restoredContext)
		restoredContext.resume()
		advanceUntilIdle()

		assertEquals(2, restored.reader().state.value.activeChapterId)
	}

	@Test
	fun `Unsaved buffer queries delegate to the scene editor service`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.hasUnsavedBuffers())
	}

	// --- actions -------------------------------------------------------------

	// root -> [G1 -> [S1, S2], G2 -> [S3]]
	private fun twoGroupTree(): ImmutableTree<SceneItem> {
		fun group(id: Int, vararg scenes: SceneItem) =
			TreeNode(SceneItem(projectDef, SceneItem.Type.Group, id, "Group $id", order = 0)).apply {
				scenes.forEach { addChild(TreeNode(it)) }
			}
		val root = TreeNode(SceneItem(projectDef, SceneItem.Type.Root, id = 0, name = "root", order = 0)).apply {
			addChild(group(101, sceneItem(1), sceneItem(2)))
			addChild(group(102, sceneItem(3)))
		}
		return Tree<SceneItem>().apply { setRoot(root) }.toImmutableTree()
	}

	private fun startedStoryEditor(): StoryEditor {
		every { sceneEditor.getSceneTree() } returns twoGroupTree()
		val comp = newComponent()
		context.resume()
		return comp
	}

	private fun StoryEditor.openSceneId(): Int? =
		(detailsRouterState.value.active.instance as? StoryEditor.ChildDestination.Detail.EditorDestination)
			?.component?.state?.value?.sceneItem?.id

	@Test
	fun `Mod+PageDown opens the next scene`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		comp.showScene(sceneItem(1))
		advanceUntilIdle()

		val handled = registry.dispatch(KeyChord.mod(ChordKey.PageDown))
		advanceUntilIdle()

		assertTrue(handled)
		assertEquals(2, comp.openSceneId())
	}

	@Test
	fun `Mod+PageUp opens the previous scene across groups`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		comp.showScene(sceneItem(3))
		advanceUntilIdle()

		registry.dispatch(KeyChord.mod(ChordKey.PageUp))
		advanceUntilIdle()

		assertEquals(2, comp.openSceneId())
	}

	@Test
	fun `Mod+PageUp at the first scene is not handled`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		comp.showScene(sceneItem(1))
		advanceUntilIdle()

		val handled = registry.dispatch(KeyChord.mod(ChordKey.PageUp))
		advanceUntilIdle()

		assertFalse(handled)
		assertEquals(1, comp.openSceneId())
	}

	@Test
	fun `Mod+Shift+PageDown opens the first scene of the next group`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		comp.showScene(sceneItem(1))
		advanceUntilIdle()

		registry.dispatch(KeyChord.mod(ChordKey.PageDown, shift = true))
		advanceUntilIdle()

		assertEquals(3, comp.openSceneId())
	}

	@Test
	fun `Mod+Shift+PageUp opens the first scene of the previous group`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		comp.showScene(sceneItem(3))
		advanceUntilIdle()

		registry.dispatch(KeyChord.mod(ChordKey.PageUp, shift = true))
		advanceUntilIdle()

		assertEquals(1, comp.openSceneId())
	}

	@Test
	fun `Mod+Shift+O toggles the outline overview`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		advanceUntilIdle()

		registry.dispatch(KeyChord.mod(ChordKey.O, shift = true))
		advanceUntilIdle()
		val opened = comp.dialogState.value.child?.instance
		registry.dispatch(KeyChord.mod(ChordKey.O, shift = true))
		advanceUntilIdle()

		assertIs<StoryEditor.ChildDestination.DialogDestination.OutlineDestination>(opened)
		assertIs<StoryEditor.ChildDestination.DialogDestination.None>(comp.dialogState.value.child?.instance)
	}

	@Test
	fun `Mod+N is blocked while the outline overview is open`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		advanceUntilIdle()
		val liveBefore = registry.isLive(HammerActions.ItemNew.id)

		comp.showOutlineOverview()
		advanceUntilIdle()
		val handled = registry.dispatch(KeyChord.mod(ChordKey.N))
		comp.dismissDialog()
		advanceUntilIdle()

		assertTrue(liveBefore)
		assertFalse(handled)
		assertTrue(registry.isLive(HammerActions.ItemNew.id))
	}

	@Test
	fun `Mod+Shift+R opens the reader when no scene is open`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		advanceUntilIdle()

		val handled = registry.dispatch(KeyChord.mod(ChordKey.R, shift = true))
		advanceUntilIdle()

		assertTrue(handled)
		assertEquals(DetailsRouter.Config.Reader(sceneDef = null), comp.detailsRouterState.value.active.configuration)
	}

	@Test
	fun `Mod+Shift+R reads the open scene`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		comp.showScene(sceneItem(2))
		advanceUntilIdle()

		registry.dispatch(KeyChord.mod(ChordKey.R, shift = true))
		advanceUntilIdle()

		assertEquals(DetailsRouter.Config.Reader(sceneDef = sceneItem(2)), comp.detailsRouterState.value.active.configuration)
	}

	@Test
	fun `Mod+Shift+R while reading goes back to editing the scene being read`() = runTest(mainTestDispatcher) {
		val comp = startedStoryEditor()
		comp.showReader(sceneItem(2))
		advanceUntilIdle()
		comp.reader().scrollHandled()

		val handled = registry.dispatch(KeyChord.mod(ChordKey.R, shift = true))
		advanceUntilIdle()

		assertTrue(handled)
		assertEquals(2, comp.openSceneId())
	}

	@Test
	fun `Mod+Shift+R while reading a story with no scenes closes the reader`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns treeOf()
		val comp = newComponent()
		context.resume()
		comp.showReader(null)
		advanceUntilIdle()

		registry.dispatch(KeyChord.mod(ChordKey.R, shift = true))
		advanceUntilIdle()

		assertFalse(comp.isDetailShown())
	}

	@Test
	fun `next and previous are not live without an open scene`() = runTest(mainTestDispatcher) {
		startedStoryEditor()
		advanceUntilIdle()

		assertFalse(registry.isLive(HammerActions.ItemNext.id))
		assertFalse(registry.isLive(HammerActions.ItemPrev.id))
		assertFalse(registry.isLive(HammerActions.StoryNextGroup.id))
		assertFalse(registry.isLive(HammerActions.StoryPrevGroup.id))
		assertTrue(registry.isLive(HammerActions.StoryToggleOutline.id))
	}

	@Test
	fun `Story actions stop answering once stopped`() = runTest(mainTestDispatcher) {
		startedStoryEditor()
		advanceUntilIdle()

		context.stop()

		assertFalse(registry.dispatch(KeyChord.mod(ChordKey.O, shift = true)))
		assertFalse(registry.dispatch(KeyChord.mod(ChordKey.R, shift = true)))
	}
}
