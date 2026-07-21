package components.storyeditor.focusmode

import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusModeComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusModeService
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FocusModeComponentTest : ComponentTest() {

	private lateinit var settingsStore: GlobalSettingsStore
	private lateinit var focusModeService: FocusModeService
	private lateinit var sceneEditor: SceneEditorService
	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>

	private val bufferCallback = slot<suspend (SceneBuffer) -> Unit>()
	private val settingsTransform = slot<(GlobalSettings) -> GlobalSettings>()

	private var closed = false

	private val sceneItem
		get() = SceneItem(projectDef, SceneItem.Type.Scene, id = 7, name = "Chapter One", order = 0)

	private fun buffer(source: UpdateSource) = SceneBuffer(
		content = SceneContent(scene = sceneItem, markdown = "words"),
		dirty = false,
		source = source,
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		closed = false
		settingsUpdates = MutableSharedFlow(extraBufferCapacity = 4)

		settingsStore = mockk(relaxed = true)
		every { settingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { settingsStore.globalSettingsUpdates } returns settingsUpdates
		coEvery { settingsStore.updateSettings(capture(settingsTransform)) } returns Unit

		focusModeService = mockk(relaxed = true)

		sceneEditor = mockk(relaxed = true)
		coEvery { sceneEditor.loadSceneBufferAsync(any()) } returns buffer(UpdateSource.Repository)
		every {
			sceneEditor.subscribeToBufferUpdates(any(), any(), capture(bufferCallback))
		} returns mockk(relaxed = true)

		val spellCheckRepository = mockk<SpellCheckRepository>(relaxed = true)
		every { spellCheckRepository.dictionaryFlow } returns MutableSharedFlow()

		setupComponentKoin(module {
			single { settingsStore }
			single { spellCheckRepository }
			single { focusModeService }
			scope<ProjectDefScope> {
				scoped { sceneEditor }
			}
		})
	}

	private fun newComponent() = FocusModeComponent(
		componentContext = context,
		projectDef = projectDef,
		sceneItem = sceneItem,
		closeFocusMode = { closed = true },
	)

	@Test
	fun `Scene content loads on create`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(comp.state.value.isLoading)
		assertEquals("words", comp.state.value.sceneBuffer?.content?.markdown)
	}

	@Test
	fun `Focus mode is entered on start and exited on stop`() = runTest(mainTestDispatcher) {
		newComponent()
		context.resume()
		advanceUntilIdle()

		verify(exactly = 1) { focusModeService.enterFocusMode() }

		context.stop()
		advanceUntilIdle()
		verify(exactly = 1) { focusModeService.exitFocusMode() }
	}

	@Test
	fun `Content edits are forwarded to the scene editor`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.onContentChanged(mockk())

		verify {
			sceneEditor.onContentChanged(
				match { it.scene.id == sceneItem.id },
				UpdateSource.Editor,
			)
		}
	}

	@Test
	fun `Text size controls persist through settings`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val startingSize = comp.state.value.textSize

		comp.increaseTextSize()
		advanceUntilIdle()
		val increased = settingsTransform.captured(GlobalSettings(projectsDirectory = "/p")).editorFontSize
		assertTrue(increased > startingSize)

		comp.decreaseTextSize()
		advanceUntilIdle()
		val decreased = settingsTransform.captured(GlobalSettings(projectsDirectory = "/p")).editorFontSize
		assertTrue(decreased < startingSize)

		comp.resetTextSize()
		advanceUntilIdle()
		val reset = settingsTransform.captured(GlobalSettings(projectsDirectory = "/p")).editorFontSize
		assertEquals(GlobalSettings.DEFAULT_FONT_SIZE, reset)
	}

	@Test
	fun `Settings updates change the text size in state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		settingsUpdates.emit(GlobalSettings(projectsDirectory = "/projects", editorFontSize = 42f))
		advanceUntilIdle()

		assertEquals(42f, comp.state.value.textSize)
	}

	@Test
	fun `External buffer updates refresh state and force an editor update`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val before = comp.lastForceUpdate.value
		bufferCallback.captured(buffer(UpdateSource.Sync))
		advanceUntilIdle()

		assertEquals("words", comp.state.value.sceneBuffer?.content?.markdown)
		assertTrue(comp.lastForceUpdate.value > before, "External update should force an editor refresh")
	}

	@Test
	fun `Editor-sourced buffer updates do not force an editor refresh`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val before = comp.lastForceUpdate.value
		bufferCallback.captured(buffer(UpdateSource.Editor))
		advanceUntilIdle()

		assertEquals(before, comp.lastForceUpdate.value)
	}

	@Test
	fun `Dismiss closes focus mode`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.dismiss()
		assertTrue(closed)
	}
}
