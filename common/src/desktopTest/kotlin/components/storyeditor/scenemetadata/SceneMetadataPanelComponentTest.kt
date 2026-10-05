package components.storyeditor.scenemetadata

import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanelComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataUpdate
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SceneMetadataPanelComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService
	private lateinit var metadataUpdateFlow: MutableSharedFlow<SceneMetadataUpdate>
	private val sceneTreeCallback = slot<(SceneSummary) -> Unit>()

	private val sceneItem
		get() = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 1,
			name = "Scene 1",
			order = 0,
		)

	@BeforeEach
	override fun setup() {
		super.setup()

		sceneEditor = mockk(relaxed = true)
		metadataUpdateFlow = MutableSharedFlow(extraBufferCapacity = 8)
		every { sceneEditor.metadataUpdateFlow } returns metadataUpdateFlow
		every { sceneEditor.subscribeToSceneUpdates(any(), capture(sceneTreeCallback)) } returns mockk(relaxed = true)
		every { sceneEditor.getSceneBuffer(any<SceneItem>()) } returns null
		every { sceneEditor.getSceneFilePathOrNull(any()) } returns null
		coEvery { sceneEditor.loadSceneMetadata(any()) } returns SceneMetadata()

		val encyclopediaService = mockk<EncyclopediaService>(relaxed = true)
		every { encyclopediaService.entryListFlow } returns MutableSharedFlow()
		coEvery { encyclopediaService.ensureEntriesLoaded() } returns emptyList()

		setupComponentKoin(module {
			single<CoroutineScope>(named(APP_SCOPE)) { scope }
			scope<ProjectDefScope> {
				scoped { sceneEditor }
				scoped { encyclopediaService }
				scoped { ScrubInvalidReferencesUseCase(mockk(relaxed = true)) }
			}
		})
	}

	private fun newComponent() = SceneMetadataPanelComponent(
		componentContext = context,
		originalSceneItem = sceneItem,
		showEntry = { },
		onShowGlobalSearchForTag = { },
	)

	private fun treeOf(scene: SceneItem): ImmutableTree<SceneItem> {
		val node = TreeValue(value = scene, index = 1, parent = 0, children = persistentListOf(), depth = 1, totalChildren = 0)
		val root = TreeValue(
			value = SceneItem(projectDef, SceneItem.Type.Root, id = 0, name = "root", order = 0),
			index = 0, parent = -1, children = persistentListOf(node), depth = 0, totalChildren = 1,
		)
		return ImmutableTree(root = root, totalChildren = 1)
	}

	@Test
	fun `A scene rename from the tree updates the title`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		sceneTreeCallback.captured.invoke(
			SceneSummary(treeOf(sceneItem.copy(name = "Renamed By Sync")), persistentSetOf())
		)
		advanceUntilIdle()

		assertEquals("Renamed By Sync", component.state.value.sceneItem.name)
	}

	@Test
	fun `A synced metadata write replaces the outline and notes`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.loadSceneMetadata(any()) } returns
			SceneMetadata(outline = "old outline", notes = "old notes")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		metadataUpdateFlow.emit(
			SceneMetadataUpdate(
				sceneItem.id,
				SceneMetadata(outline = "server outline", notes = "server notes"),
				UpdateSource.Sync,
			)
		)
		advanceUntilIdle()

		assertEquals("server outline", component.state.value.metadata.outline)
		assertEquals("server notes", component.state.value.metadata.notes)
	}

	@Test
	fun `A synced metadata write keeps an unsaved outline edit`() = runTest(mainTestDispatcher) {
		// The debounced store never completes, so the local edit stays unsaved.
		coEvery { sceneEditor.storeMetadata(any(), any()) } coAnswers { awaitCancellation() }
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.updateOutline("my unsaved outline")
		metadataUpdateFlow.emit(
			SceneMetadataUpdate(
				sceneItem.id,
				SceneMetadata(outline = "server outline", tags = setOf("synced")),
				UpdateSource.Sync,
			)
		)
		advanceUntilIdle()

		assertEquals("my unsaved outline", component.state.value.metadata.outline)
		assertEquals(setOf("synced"), component.state.value.metadata.tags)
	}

	@Test
	fun `A non-sync metadata write keeps the local outline`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.loadSceneMetadata(any()) } returns SceneMetadata(outline = "local outline")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		metadataUpdateFlow.emit(
			SceneMetadataUpdate(
				sceneItem.id,
				SceneMetadata(outline = "stale outline", tags = setOf("auto")),
				UpdateSource.Editor,
			)
		)
		advanceUntilIdle()

		assertEquals("local outline", component.state.value.metadata.outline)
		assertEquals(setOf("auto"), component.state.value.metadata.tags)
	}

	@Test
	fun `Destroy flushes pending metadata edits`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.updateOutline("Final outline edit")
		context.destroy()
		advanceUntilIdle()

		coVerify {
			sceneEditor.storeMetadata(
				match { it.outline == "Final outline edit" },
				sceneItem.id,
			)
		}
	}

	@Test
	fun `Destroy flush IO failure does not propagate`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.storeMetadata(any(), any()) } throws IOException("volume gone")

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.updateOutline("Final outline edit")
		context.destroy()
		advanceUntilIdle()

		coVerify {
			sceneEditor.storeMetadata(any(), sceneItem.id)
		}
	}

	@Test
	fun `Destroy before the metadata load completes does not overwrite stored metadata`() =
		runTest(mainTestDispatcher) {
			coEvery { sceneEditor.loadSceneMetadata(any()) } coAnswers { awaitCancellation() }

			val component = newComponent()
			context.resume()
			advanceUntilIdle()

			context.destroy()
			advanceUntilIdle()

			// State still holds the default empty SceneMetadata; flushing it would wipe the
			// scene's real outline/notes/references on disk.
			coVerify(exactly = 0) {
				sceneEditor.storeMetadata(any(), any())
			}
		}

	@Test
	fun `Destroy after the project scope closed does not crash and still flushes metadata`() =
		runTest(mainTestDispatcher) {
			val component = newComponent()
			context.resume()
			advanceUntilIdle()

			component.updateOutline("Final outline edit")

			// Project close order on desktop: the Koin scope closes first, then Compose
			// disposal destroys the components.
			component.projectScope.scope.close()
			context.destroy()
			advanceUntilIdle()

			coVerify {
				sceneEditor.storeMetadata(
					match { it.outline == "Final outline edit" },
					sceneItem.id,
				)
			}
		}
}
