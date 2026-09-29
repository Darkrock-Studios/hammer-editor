package components.storyeditor.scenemetadata

import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanelComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
	private lateinit var metadataUpdates: MutableSharedFlow<Pair<Int, SceneMetadata>>

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
		metadataUpdates = MutableSharedFlow(extraBufferCapacity = 8)
		every { sceneEditor.metadataUpdateFlow } returns metadataUpdates
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

	@Test
	fun `External outline and notes are taken when there is no local edit`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.loadSceneMetadata(any()) } returns SceneMetadata(outline = "Old outline", notes = "Old notes")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		metadataUpdates.emit(sceneItem.id to SceneMetadata(outline = "New outline", notes = "New notes"))
		advanceUntilIdle()

		assertEquals("New outline", component.state.value.metadata.outline)
		assertEquals("New notes", component.state.value.metadata.notes)
	}

	@Test
	fun `A pending local edit is kept over an external write`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.loadSceneMetadata(any()) } returns SceneMetadata(outline = "Old outline", notes = "Old notes")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.updateOutline("My outline")
		metadataUpdates.emit(sceneItem.id to SceneMetadata(outline = "New outline", notes = "New notes"))
		runCurrent()

		assertEquals("My outline", component.state.value.metadata.outline)
		assertEquals("New notes", component.state.value.metadata.notes)

		advanceUntilIdle()
		coVerify {
			sceneEditor.storeMetadata(match { it.outline == "My outline" && it.notes == "New notes" }, sceneItem.id)
		}
	}
}
