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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest

@OptIn(ExperimentalCoroutinesApi::class)
class SceneMetadataPanelComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService

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
		every { sceneEditor.metadataUpdateFlow } returns MutableSharedFlow()
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
