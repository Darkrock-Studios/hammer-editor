package components.storyeditor.outlineoverview

import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverview
import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverviewComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OutlineOverviewComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService

	private var dismissCount = 0
	private var shownScene: SceneItem? = null

	private val chapter = group(id = 10, name = "Chapter One")
	private val nestedScene = scene(id = 12, name = "Nested Scene")
	private val nestedGroup = group(id = 13, name = "Nested Group")
	private val topScene = scene(id = 11, name = "Top Scene")

	@BeforeEach
	override fun setup() {
		super.setup()

		dismissCount = 0
		shownScene = null

		sceneEditor = mockk()
		every { sceneEditor.getSceneTree() } returns sceneTree()
		coEvery { sceneEditor.loadSceneMetadata(nestedScene.id) } returns SceneMetadata(outline = "nested outline")
		coEvery { sceneEditor.loadSceneMetadata(topScene.id) } returns SceneMetadata(outline = "top outline")

		setupComponentKoin(module {
			scope<ProjectDefScope> {
				scoped { sceneEditor }
			}
		})
	}

	private fun scene(id: Int, name: String) = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = id,
		name = name,
		order = id,
	)

	private fun group(id: Int, name: String) = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Group,
		id = id,
		name = name,
		order = id,
	)

	private fun sceneTree(): ImmutableTree<SceneItem> {
		val nestedSceneNode = TreeValue(
			value = nestedScene,
			index = 2,
			parent = 1,
			children = persistentListOf(),
			depth = 2,
			totalChildren = 0,
		)
		val nestedGroupNode = TreeValue(
			value = nestedGroup,
			index = 3,
			parent = 1,
			children = persistentListOf(),
			depth = 2,
			totalChildren = 0,
		)
		val chapterNode = TreeValue(
			value = chapter,
			index = 1,
			parent = 0,
			children = persistentListOf(nestedSceneNode, nestedGroupNode),
			depth = 1,
			totalChildren = 2,
		)
		val topSceneNode = TreeValue(
			value = topScene,
			index = 4,
			parent = 0,
			children = persistentListOf(),
			depth = 1,
			totalChildren = 0,
		)
		val root = TreeValue(
			value = SceneItem(
				projectDef = projectDef,
				type = SceneItem.Type.Root,
				id = SceneItem.ROOT_ID,
				name = "",
				order = 0,
			),
			index = 0,
			parent = -1,
			children = persistentListOf(chapterNode, topSceneNode),
			depth = 0,
			totalChildren = 4,
		)
		return ImmutableTree(root = root, totalChildren = 4)
	}

	private fun newComponent() = OutlineOverviewComponent(
		componentContext = context,
		projectDef = projectDef,
		dismissDialog = { dismissCount++ },
		showScene = { shownScene = it },
	)

	@Test
	fun `the outline lists chapters and scenes in story order`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(
			listOf(
				OutlineOverview.OutlineItem.ChapterOutline(sceneItem = chapter),
				OutlineOverview.OutlineItem.SceneOutline(sceneItem = nestedScene, outline = "nested outline"),
				OutlineOverview.OutlineItem.SceneOutline(sceneItem = topScene, outline = "top outline"),
			),
			comp.state.value.overview,
		)
	}

	@Test
	fun `nested groups and the root are excluded from the outline`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val items = comp.state.value.overview.map { item ->
			when (item) {
				is OutlineOverview.OutlineItem.ChapterOutline -> item.sceneItem
				is OutlineOverview.OutlineItem.SceneOutline -> item.sceneItem
			}
		}
		assertTrue(items.none { it.id == nestedGroup.id })
		assertTrue(items.none { it.type == SceneItem.Type.Root })
	}

	@Test
	fun `dismiss invokes the dismiss callback`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.dismiss()

		assertEquals(1, dismissCount)
		assertNull(shownScene)
	}

	@Test
	fun `selectScene shows the scene and closes the dialog`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.selectScene(topScene)

		assertEquals(topScene, shownScene)
		assertEquals(1, dismissCount)
	}
}
