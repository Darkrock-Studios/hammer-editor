package components.storyeditor.scenelist

import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneListComponent
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.emptySceneSummary
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SceneListComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService
	private lateinit var selectedSceneFlow: MutableSharedFlow<SceneItem?>
	private val bufferCallback = slot<suspend (SceneBuffer) -> Unit>()
	private val sceneListCallback = slot<(SceneSummary) -> Unit>()

	private val selectedScenes = mutableListOf<SceneItem>()
	private var outlineShown = false

	@BeforeEach
	override fun setup() {
		super.setup()

		selectedScenes.clear()
		outlineShown = false
		selectedSceneFlow = MutableSharedFlow(replay = 1)

		sceneEditor = mockk(relaxed = true)
		every { sceneEditor.getSceneSummaries() } returns emptySceneSummary(projectDef)
		every {
			sceneEditor.subscribeToBufferUpdates(any(), any(), capture(bufferCallback))
		} returns mockk(relaxed = true)
		every {
			sceneEditor.subscribeToSceneUpdates(any(), capture(sceneListCallback))
		} returns mockk(relaxed = true)

		setupComponentKoin(module {
			scope<ProjectDefScope> {
				scoped { sceneEditor }
			}
		})
	}

	private fun newComponent() = SceneListComponent(
		componentContext = context,
		projectDef = projectDef,
		selectedSceneItem = selectedSceneFlow,
		sceneSelected = { selectedScenes.add(it) },
		showOutlineOverviewDialog = { outlineShown = true },
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
	fun `Creating a scene under the root passes a null parent and selects the new scene`() =
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
			assertEquals(listOf(created), selectedScenes)
		}

	@Test
	fun `Failed scene creation selects nothing`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.createScene(any(), any()) } returns null

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.createScene(null, "Nope")
		advanceUntilIdle()

		assertTrue(selectedScenes.isEmpty())
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
	fun `Unarchiving selects the restored scene and closes the dialog`() = runTest(mainTestDispatcher) {
		val archived = sceneItem(8).copy(archived = true)
		val restored = archived.copy(archived = false)
		coEvery { sceneEditor.unarchiveScene(archived) } returns restored

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showArchivedScenes()
		comp.unarchiveScene(archived)
		advanceUntilIdle()

		assertEquals(listOf(restored), selectedScenes)
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

		assertTrue(selectedScenes.isEmpty())
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
	fun `Rename delegates to the repository`() = runTest(mainTestDispatcher) {
		val scene = sceneItem(1)
		coEvery { sceneEditor.renameScene(scene, "Renamed") } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.renameScene(scene, "Renamed"))
		coVerify { sceneEditor.renameScene(scene, "Renamed") }
	}
}
