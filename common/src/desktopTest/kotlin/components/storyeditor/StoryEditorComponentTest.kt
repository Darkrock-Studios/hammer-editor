package components.storyeditor

import com.arkivanov.decompose.value.getValue
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditor
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditorComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditorComponent
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.references.AutoConfirmReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryEditorComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService
	private lateinit var settingsStore: GlobalSettingsStore

	private val sceneTreeCallbacks = mutableListOf<(SceneSummary) -> Unit>()
	private val addedMenus = mutableListOf<MenuDescriptor>()
	private var focusModeScene: SceneItem? = null

	@BeforeEach
	override fun setup() {
		super.setup()

		sceneTreeCallbacks.clear()
		addedMenus.clear()
		focusModeScene = null

		settingsStore = mockk(relaxed = true)
		every { settingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { settingsStore.globalSettingsUpdates } returns MutableSharedFlow()

		sceneEditor = mockk(relaxed = true)
		every { sceneEditor.subscribeToSceneUpdates(any(), capture(sceneTreeCallbacks)) } returns mockk(relaxed = true)

		val spellCheckRepository = mockk<SpellCheckRepository>(relaxed = true)
		every { spellCheckRepository.dictionaryFlow } returns MutableSharedFlow()

		val encyclopediaService = mockk<EncyclopediaService>(relaxed = true)
		every { encyclopediaService.entryListFlow } returns MutableSharedFlow()

		setupComponentKoin(module {
			single<CoroutineScope>(named(APP_SCOPE)) { scope }
			single { settingsStore }
			single { spellCheckRepository }
			scope<ProjectDefScope> {
				scoped { sceneEditor }
				scoped<SceneDraftRepository> { mockk(relaxed = true) }
				scoped<AutoConfirmReferencesUseCase> { mockk(relaxed = true) }
				scoped<ScrubInvalidReferencesUseCase> { mockk(relaxed = true) }
				scoped { encyclopediaService }
			}
		})
	}

	private fun newComponent() = StoryEditorComponent(
		componentContext = context,
		projectDef = projectDef,
		addMenu = { addedMenus.add(it) },
		removeMenu = { },
		showFocusMode = { focusModeScene = it },
		showEntry = { },
		showGlobalSearchForTag = { },
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

			val viewDrafts = addedMenus.flatMap { it.items }
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

	@Test
	fun `Unsaved buffer queries delegate to the scene editor service`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.hasUnsavedBuffers())
	}
}
