package components.storyeditor.sceneeditor

import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditorComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.increaseEditorTextSize
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.references.AutoConfirmReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import io.mockk.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
// These tests drive lifecycle by calling onCreate()/onStart()/onStop() directly rather than
// context.resume(), which keeps the eagerly-constructed child SceneMetadataPanelComponent dormant
// so its dependencies don't need registering.
class SceneEditorComponentTest : ComponentTest() {

	private val sceneItem = SceneItem(projectDef, SceneItem.Type.Scene, id = 7, name = "Chapter One", order = 0)

	private lateinit var settingsStore: GlobalSettingsStore
	private lateinit var sceneEditor: SceneEditorService
	private lateinit var draftsRepository: SceneDraftRepository
	private lateinit var autoConfirm: AutoConfirmReferencesUseCase
	private lateinit var spellCheck: SpellCheckRepository

	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var dictionaryFlow: MutableSharedFlow<PlatformSpellChecker?>
	private val bufferCallback = slot<suspend (SceneBuffer) -> Unit>()
	private val sceneTreeCallback = slot<(SceneSummary) -> Unit>()
	private val settingsAction = slot<(GlobalSettings) -> GlobalSettings>()

	private var globalSettings = settings()

	private var closeCount = 0
	private var focusShownFor: SceneItem? = null
	private val addedMenus = mutableListOf<MenuDescriptor>()
	private val removedMenuIds = mutableListOf<String>()

	private fun settings(
		spellCheckEnabled: Boolean = true,
		metadataVisible: Boolean = true,
		fontSize: Float = GlobalSettings.DEFAULT_FONT_SIZE,
	) = GlobalSettings(
		projectsDirectory = "",
		editorFontSize = fontSize,
		metadataPanelVisible = metadataVisible,
		spellCheckSettings = SpellCheckerSettings(enabled = spellCheckEnabled, locale = mockk()),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		settingsStore = mockk(relaxed = true)
		sceneEditor = mockk(relaxed = true)
		draftsRepository = mockk(relaxed = true)
		autoConfirm = mockk(relaxed = true)
		spellCheck = mockk(relaxed = true)

		settingsUpdates = MutableSharedFlow(extraBufferCapacity = 4)
		dictionaryFlow = MutableSharedFlow(extraBufferCapacity = 1)
		globalSettings = settings()

		every { settingsStore.globalSettings } answers { globalSettings }
		every { settingsStore.globalSettingsUpdates } returns settingsUpdates
		coEvery { settingsStore.updateSettings(capture(settingsAction)) } returns Unit
		every { spellCheck.dictionaryFlow } returns dictionaryFlow
		every {
			sceneEditor.subscribeToBufferUpdates(
				any(),
				any(),
				capture(bufferCallback)
			)
		} returns mockk(relaxed = true)
		every { sceneEditor.subscribeToSceneUpdates(any(), capture(sceneTreeCallback)) } returns mockk(relaxed = true)

		setupComponentKoin(module {
			single<GlobalSettingsStore> { settingsStore }
			single<SpellCheckRepository> { spellCheck }
			single { sceneEditor } bind SceneEditorService::class
			single { draftsRepository } bind SceneDraftRepository::class
			single { autoConfirm } bind AutoConfirmReferencesUseCase::class
		})

		closeCount = 0
		focusShownFor = null
		addedMenus.clear()
		removedMenuIds.clear()
	}

	private fun newComponent() = SceneEditorComponent(
		componentContext = context,
		originalSceneItem = sceneItem,
		addMenu = { addedMenus.add(it) },
		removeMenu = { removedMenuIds.add(it) },
		closeSceneEditor = { closeCount++ },
		showDraftsList = {},
		showFocusMode = { focusShownFor = it },
		showEntry = {},
		showGlobalSearchForTag = {},
	)

	private fun buffer(source: UpdateSource) =
		SceneBuffer(content = SceneContent(scene = sceneItem, markdown = "text"), dirty = false, source = source)

	// A minimal tree (root -> the scene) so onSceneTreeUpdate's findBy can locate id 7.
	private fun treeOf(scene: SceneItem): ImmutableTree<SceneItem> {
		val node = TreeValue(value = scene, index = 1, parent = 0, children = persistentListOf(), depth = 1, totalChildren = 0)
		val root = TreeValue(
			value = SceneItem(projectDef, SceneItem.Type.Root, id = 0, name = "root", order = 0),
			index = 0, parent = -1, children = persistentListOf(node), depth = 0, totalChildren = 1,
		)
		return ImmutableTree(root = root, totalChildren = 1)
	}

	// --- initial state -------------------------------------------------------

	@Test
	fun `initial state reflects the spell-check and metadata settings`() = runTest(mainTestDispatcher) {
		globalSettings = settings(spellCheckEnabled = false, metadataVisible = false)

		val comp = newComponent()

		assertFalse(comp.state.value.spellCheckingEnabled)
		assertFalse(comp.state.value.metadataPanelVisible)
	}

	// --- scene name ----------------------------------------------------------

	@Test
	fun `name edit flags toggle`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.beginSceneNameEdit()
		assertTrue(comp.state.value.isEditingName)

		comp.endSceneNameEdit()
		assertFalse(comp.state.value.isEditingName)
	}

	@Test
	fun `changeSceneName with a valid name renames the scene and updates the item`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.beginSceneNameEdit()

			comp.changeSceneName("Chapter Two")

			coVerify { sceneEditor.renameScene(sceneItem, "Chapter Two") }
			assertEquals("Chapter Two", comp.state.value.sceneItem.name)
			assertFalse(comp.state.value.isEditingName)
		}

	@Test
	fun `changeSceneName with an invalid name does not rename and stays in edit mode`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.beginSceneNameEdit()

			comp.changeSceneName("")

			coVerify(exactly = 0) { sceneEditor.renameScene(any(), any()) }
			assertTrue(comp.state.value.isEditingName)
			assertEquals("Chapter One", comp.state.value.sceneItem.name)
		}

	// --- delete / archive / discard -----------------------------------------

	@Test
	fun `delete flags toggle`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.beginDelete()
		assertTrue(comp.state.value.confirmDelete)

		comp.endDelete()
		assertFalse(comp.state.value.confirmDelete)
	}

	@Test
	fun `doDelete deletes the scene, clears the prompt, and closes the editor`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.beginDelete()

			comp.doDelete()
			advanceUntilIdle()

			coVerify { sceneEditor.deleteScene(sceneItem) }
			assertFalse(comp.state.value.confirmDelete)
			assertEquals(1, closeCount)
		}

	@Test
	fun `archive flags toggle`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.beginArchive()
		assertTrue(comp.state.value.confirmArchive)

		comp.endArchive()
		assertFalse(comp.state.value.confirmArchive)
	}

	@Test
	fun `doArchive archives the scene, clears the prompt, and closes the editor`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.beginArchive()

			comp.doArchive()
			advanceUntilIdle()

			coVerify { sceneEditor.archiveScene(sceneItem) }
			assertFalse(comp.state.value.confirmArchive)
			assertEquals(1, closeCount)
		}

	@Test
	fun `discard flags toggle`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.beginDiscard()
		assertTrue(comp.state.value.confirmDiscard)

		comp.endDiscard()
		assertFalse(comp.state.value.confirmDiscard)
	}

	@Test
	fun `doDiscard discards the buffer, clears the prompt, and forces a refresh`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.beginDiscard()

			comp.doDiscard()

			verify { sceneEditor.discardSceneBuffer(sceneItem) }
			assertFalse(comp.state.value.confirmDiscard)
			assertTrue(comp.lastForceUpdate.value > 0)
		}

	// --- drafts --------------------------------------------------------------

	@Test
	fun `save-draft flags toggle`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.beginSaveDraft()
		assertTrue(comp.state.value.isSavingDraft)

		comp.endSaveDraft()
		assertFalse(comp.state.value.isSavingDraft)
	}

	@Test
	fun `saveDraft saves under the draft name and renames the metadata to the new name`() =
		runTest(mainTestDispatcher) {
			coEvery { draftsRepository.saveDraft(sceneItem, "draft one") } returns
				DraftDef(id = 1, sceneId = 7, draftTimestamp = Instant.fromEpochSeconds(5), draftName = "draft one")
			val comp = newComponent()

			// Distinct names so we can tell draftName (used for the save) from newDraftName
			// (pushed into the metadata child).
			val result = comp.saveDraft(draftName = "draft one", newDraftName = "renamed draft")

			assertTrue(result)
			coVerify { draftsRepository.saveDraft(sceneItem, "draft one") }
			assertEquals("renamed draft", comp.sceneMetadataComponent.state.value.metadata.currentDraftName)
		}

	@Test
	fun `saveDraft with a valid name but a failed save returns false`() = runTest(mainTestDispatcher) {
		coEvery { draftsRepository.saveDraft(sceneItem, "draft one") } returns null
		val comp = newComponent()

		val result = comp.saveDraft("draft one", "draft one")

		assertFalse(result)
	}

	@Test
	fun `saveDraft with an invalid name returns false without saving`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		val result = comp.saveDraft("bad/name", "bad/name")

		assertFalse(result)
		coVerify(exactly = 0) { draftsRepository.saveDraft(any(), any()) }
	}

	// --- content -------------------------------------------------------------

	@Test
	fun `onContentChanged forwards the content to the editor as an Editor update`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			val rich = mockk<PlatformRichText>()
			val content = slot<SceneContent>()

			comp.onContentChanged(rich)

			// SceneContent.equals ignores the scene field, so capture and assert it explicitly.
			verify { sceneEditor.onContentChanged(capture(content), UpdateSource.Editor) }
			assertEquals(sceneItem, content.captured.scene)
			assertEquals(rich, content.captured.platformRepresentation)
		}

	@Test
	fun `storeSceneContent auto-confirms references before flushing the buffer`() =
		runTest(mainTestDispatcher) {
			coEvery { sceneEditor.storeSceneBuffer(sceneItem) } returns true
			val comp = newComponent()

			val result = comp.storeSceneContent()

			assertTrue(result)
			// Ordering is load-bearing: the metadata write must piggyback the buffer save's dirty-mark.
			coVerifyOrder {
				autoConfirm(sceneItem)
				sceneEditor.storeSceneBuffer(sceneItem)
			}
		}

	// --- metadata + text size (settings writes) ------------------------------

	@Test
	fun `toggleMetadataModal flips the modal flag`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		assertFalse(comp.state.value.showMetadataModal)

		comp.toggleMetadataModal()
		assertTrue(comp.state.value.showMetadataModal)

		comp.toggleMetadataModal()
		assertFalse(comp.state.value.showMetadataModal)
	}

	@Test
	fun `toggleMetadataPanelVisible flips the persisted setting`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.toggleMetadataPanelVisible()
		advanceUntilIdle()

		// metadataPanelVisible starts true; the persisted action flips it.
		assertFalse(settingsAction.captured(globalSettings).metadataPanelVisible)
	}

	@Test
	fun `resetTextSize persists the default font size`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.resetTextSize()
		advanceUntilIdle()

		assertEquals(GlobalSettings.DEFAULT_FONT_SIZE, settingsAction.captured(globalSettings).editorFontSize)
	}

	@Test
	fun `increaseTextSize persists a larger font size`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.increaseTextSize()
		advanceUntilIdle()

		val expected = increaseEditorTextSize(GlobalSettings.DEFAULT_FONT_SIZE)
		assertEquals(expected, settingsAction.captured(globalSettings).editorFontSize)
		assertTrue(expected > GlobalSettings.DEFAULT_FONT_SIZE)
	}

	// --- forwarding ----------------------------------------------------------

	@Test
	fun `enterFocusMode forwards the scene to the parent`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.enterFocusMode()
		assertEquals(sceneItem, focusShownFor)
	}

	@Test
	fun `closeEditor invokes the parent callback`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.closeEditor()

		assertEquals(1, closeCount)
	}

	// --- buffer + lifecycle --------------------------------------------------

	@Test
	fun `loadSceneContent loads the buffer and clears the loading flag`() = runTest(mainTestDispatcher) {
		val loaded = buffer(UpdateSource.Repository)
		coEvery { sceneEditor.loadSceneBufferAsync(sceneItem) } returns loaded
		val comp = newComponent()

		comp.loadSceneContent()
		advanceUntilIdle()

		assertEquals(loaded, comp.state.value.sceneBuffer)
		assertFalse(comp.state.value.isLoading)
	}

	@Test
	fun `a non-editor buffer update stores the buffer and forces a refresh`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.loadSceneBufferAsync(sceneItem) } returns buffer(UpdateSource.Repository)
		val comp = newComponent()
		comp.onCreate()
		advanceUntilIdle()

		val update = buffer(UpdateSource.Sync)
		bufferCallback.captured.invoke(update)
		advanceUntilIdle()

		assertEquals(update, comp.state.value.sceneBuffer)
		assertTrue(comp.lastForceUpdate.value > 0)
	}

	@Test
	fun `an editor buffer update stores the buffer without forcing a refresh`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.loadSceneBufferAsync(sceneItem) } returns buffer(UpdateSource.Repository)
		val comp = newComponent()
		comp.onCreate()
		advanceUntilIdle()

		val update = buffer(UpdateSource.Editor)
		bufferCallback.captured.invoke(update)
		advanceUntilIdle()

		assertEquals(update, comp.state.value.sceneBuffer)
		assertEquals(0, comp.lastForceUpdate.value)
	}

	@Test
	fun `an external scene rename from the tree updates the scene item`() = runTest(mainTestDispatcher) {
		coEvery { sceneEditor.loadSceneBufferAsync(sceneItem) } returns buffer(UpdateSource.Repository)
		val comp = newComponent()
		comp.onCreate()
		advanceUntilIdle()

		val renamed = sceneItem.copy(name = "Renamed Externally")
		sceneTreeCallback.captured.invoke(SceneSummary(treeOf(renamed), persistentSetOf()))

		assertEquals("Renamed Externally", comp.state.value.sceneItem.name)
	}

	@Test
	fun `settings updates flip spell-check, metadata, and font size in state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		comp.onCreate()
		advanceUntilIdle()

		settingsUpdates.emit(settings(spellCheckEnabled = false, metadataVisible = false, fontSize = 22f))
		advanceUntilIdle()

		assertFalse(comp.state.value.spellCheckingEnabled)
		assertFalse(comp.state.value.metadataPanelVisible)
		assertEquals(22f, comp.state.value.textSize)
	}

	@Test
	fun `the editor menu is added on start and removed on stop`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.onStart()
		assertEquals(1, addedMenus.size)
		assertTrue(comp.state.value.menuItems.isNotEmpty())

		comp.onStop()
		assertEquals(listOf(addedMenus.single().id), removedMenuIds)
		assertTrue(comp.state.value.menuItems.isEmpty())
	}
}
