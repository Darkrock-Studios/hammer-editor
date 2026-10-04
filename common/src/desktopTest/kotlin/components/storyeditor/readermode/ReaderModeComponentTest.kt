package components.storyeditor.readermode

import com.darkrockstudios.apps.hammer.common.components.storyeditor.readermode.ReaderModeComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.EDITOR_TEXT_SIZES
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderModeComponentTest : ComponentTest() {

	private lateinit var sceneEditor: SceneEditorService
	private lateinit var settingsStore: GlobalSettingsStore
	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>

	private lateinit var sceneUpdates: MutableSharedFlow<SceneSummary>
	private val bufferCallback = slot<suspend (SceneBuffer) -> Unit>()
	private val settingsTransform = slot<(GlobalSettings) -> GlobalSettings>()

	private var closed = false
	private var editedScene: SceneItem? = null
	private val scenesInView = mutableListOf<SceneItem?>()

	private val chapterOne = item(10, SceneItem.Type.Group)
	private val sceneA = item(11, SceneItem.Type.Scene)
	private val sceneB = item(12, SceneItem.Type.Scene)
	private val chapterTwo = item(20, SceneItem.Type.Group)
	private val sceneC = item(21, SceneItem.Type.Scene)
	private val loneScene = item(30, SceneItem.Type.Scene)

	private fun item(id: Int, type: SceneItem.Type, name: String = "Item $id") =
		SceneItem(projectDef = projectDef, type = type, id = id, name = name, order = id)

	private fun tree(vararg chapters: Pair<SceneItem, List<SceneItem>>): ImmutableTree<SceneItem> {
		val root = TreeNode(item(SceneItem.ROOT_ID, SceneItem.Type.Root))
		chapters.forEach { (chapter, scenes) ->
			val node = TreeNode(chapter)
			scenes.forEach { node.addChild(TreeNode(it)) }
			root.addChild(node)
		}
		return Tree<SceneItem>().apply { setRoot(root) }.toImmutableTree()
	}

	private fun fullTree() = tree(
		chapterOne to listOf(sceneA, sceneB),
		chapterTwo to listOf(sceneC),
		loneScene to emptyList(),
	)

	private fun summary(tree: ImmutableTree<SceneItem>) = SceneSummary(tree, persistentSetOf())

	@BeforeEach
	override fun setup() {
		super.setup()

		closed = false
		editedScene = null
		scenesInView.clear()
		settingsUpdates = MutableSharedFlow(extraBufferCapacity = 4)

		settingsStore = mockk(relaxed = true)
		every { settingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { settingsStore.globalSettingsUpdates } returns settingsUpdates
		coEvery { settingsStore.updateSettings(capture(settingsTransform)) } returns Unit

		sceneEditor = mockk(relaxed = true)
		every { sceneEditor.getSceneTree() } returns fullTree()
		every { sceneEditor.getCurrentSceneContentOrNull(any()) } answers { "text of ${firstArg<SceneItem>().id}" }
		sceneUpdates = MutableSharedFlow(replay = 1)
		every { sceneEditor.sceneListChannel } returns sceneUpdates
		every {
			sceneEditor.subscribeToBufferUpdates(any(), any(), capture(bufferCallback))
		} returns mockk(relaxed = true)

		setupComponentKoin(module {
			single { settingsStore }
			scope<ProjectDefScope> {
				scoped { sceneEditor }
			}
		})
	}

	private fun newComponent(
		initialScene: SceneItem? = null,
		componentContext: TestComponentContext = context,
	) = ReaderModeComponent(
		componentContext = componentContext,
		projectDef = projectDef,
		initialScene = initialScene,
		closeReader = { closed = true },
		openSceneEditor = { editedScene = it },
		onSceneInView = { scenesInView.add(it) },
	)

	private fun ReaderModeComponent.sceneIds() = state.value.scenes.map { it.sceneItem.id }

	@Test
	fun `Opens on the first chapter with its scenes stitched in order`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = comp.state.value
		assertEquals(listOf(10, 20, 30), state.chapters.map { it.chapter.id })
		assertEquals(listOf(2, 1, 1), state.chapters.map { it.sceneCount })
		assertEquals(chapterOne.id, state.activeChapterId)
		assertEquals(listOf("text of 11", "text of 12"), state.scenes.map { it.markdown })
		assertFalse(state.isLoading)
		assertNull(state.scrollToSceneId)
	}

	@Test
	fun `Opens on the chapter holding the initial scene and asks to scroll to it`() = runTest(mainTestDispatcher) {
		val comp = newComponent(initialScene = sceneC)
		context.resume()
		advanceUntilIdle()

		assertEquals(chapterTwo.id, comp.state.value.activeChapterId)
		assertEquals(sceneC.id, comp.state.value.scrollToSceneId)
		assertEquals(listOf(sceneC.id), comp.sceneIds())
	}

	@Test
	fun `Next and previous stop at the ends of the book`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.previousChapter()
		advanceUntilIdle()
		assertEquals(chapterOne.id, comp.state.value.activeChapterId)

		comp.nextChapter()
		comp.nextChapter()
		comp.nextChapter()
		advanceUntilIdle()

		assertEquals(loneScene.id, comp.state.value.activeChapterId)
		assertEquals(listOf(loneScene.id), comp.sceneIds())
	}

	@Test
	fun `showScene switches chapter and requests a scroll`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showScene(sceneC)
		advanceUntilIdle()

		assertEquals(chapterTwo.id, comp.state.value.activeChapterId)
		assertEquals(sceneC.id, comp.state.value.scrollToSceneId)
		assertEquals(listOf("text of 21"), comp.state.value.scenes.map { it.markdown })
	}

	@Test
	fun `Scene in view is not reported while a scroll is pending`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showScene(sceneB)
		comp.sceneInView(sceneA)

		assertTrue(scenesInView.isEmpty())

		comp.scrollHandled()

		assertEquals(listOf<SceneItem?>(sceneB), scenesInView)
		assertEquals(sceneB, comp.state.value.sceneInView)
		assertNull(comp.state.value.scrollToSceneId)
	}

	@Test
	fun `Scene in view is reported only when it changes`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.sceneInView(sceneA)
		comp.sceneInView(sceneA)
		comp.sceneInView(sceneB)

		assertEquals(listOf<SceneItem?>(sceneA, sceneB), scenesInView)
	}

	@Test
	fun `Showing a scene the reader does not know re-reports the scene in view`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.sceneInView(sceneA)

		comp.showScene(SceneItem(projectDef, SceneItem.Type.Scene, id = 99, name = "Unknown", order = 99))

		assertEquals(listOf<SceneItem?>(sceneA, sceneA), scenesInView)
		assertNull(comp.state.value.scrollToSceneId)
	}

	@Test
	fun `Opening the reader does not force a scene list reload`() = runTest(mainTestDispatcher) {
		newComponent()
		context.resume()
		advanceUntilIdle()

		verify(exactly = 0) { sceneEditor.subscribeToSceneUpdates(any(), any()) }
	}

	@Test
	fun `Edit opens the scene in view, or the first scene when none is`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.editScene()
		assertEquals(sceneA, editedScene)

		comp.sceneInView(sceneB)
		comp.editScene()
		assertEquals(sceneB, editedScene)
	}

	@Test
	fun `An update with an unchanged tree does not reload the chapter`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		sceneUpdates.emit(summary(fullTree()))
		advanceUntilIdle()

		verify(exactly = 2) { sceneEditor.getCurrentSceneContentOrNull(any()) }
		assertEquals(listOf(11, 12), comp.sceneIds())
	}

	@Test
	fun `A scene added to the active chapter reloads it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val added = item(13, SceneItem.Type.Scene)
		sceneUpdates.emit(
			summary(tree(chapterOne to listOf(sceneA, sceneB, added), chapterTwo to listOf(sceneC)))
		)
		advanceUntilIdle()

		assertEquals(listOf(11, 12, 13), comp.sceneIds())
		assertEquals(listOf(3, 1), comp.state.value.chapters.map { it.sceneCount })
	}

	@Test
	fun `A rename refreshes names without reloading`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val renamed = sceneA.copy(name = "Renamed")
		sceneUpdates.emit(
			summary(tree(chapterOne to listOf(renamed, sceneB), chapterTwo to listOf(sceneC), loneScene to emptyList()))
		)
		advanceUntilIdle()

		assertEquals("Renamed", comp.state.value.scenes.first().sceneItem.name)
		verify(exactly = 2) { sceneEditor.getCurrentSceneContentOrNull(any()) }
	}

	@Test
	fun `Removing the active chapter moves to its neighbour instead of closing`() = runTest(mainTestDispatcher) {
		val comp = newComponent(initialScene = loneScene)
		context.resume()
		advanceUntilIdle()

		sceneUpdates.emit(summary(tree(chapterOne to listOf(sceneA, sceneB), chapterTwo to listOf(sceneC))))
		advanceUntilIdle()

		assertFalse(closed)
		assertEquals(chapterTwo.id, comp.state.value.activeChapterId)
		assertEquals(listOf(sceneC.id), comp.sceneIds())
	}

	@Test
	fun `An emptied book shows no chapters and recovers when scenes return`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		sceneUpdates.emit(summary(tree()))
		advanceUntilIdle()

		assertFalse(closed)
		assertTrue(comp.state.value.chapters.isEmpty())
		assertNull(comp.state.value.activeChapterId)
		assertFalse(comp.state.value.isLoading)

		sceneUpdates.emit(summary(fullTree()))
		advanceUntilIdle()

		assertEquals(chapterOne.id, comp.state.value.activeChapterId)
		assertEquals(listOf(11, 12), comp.sceneIds())
	}

	@Test
	fun `A buffer update that arrives while the chapter loads is not lost`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		bufferCallback.captured(
			SceneBuffer(SceneContent(sceneB, markdown = "fresh words"), dirty = true, source = UpdateSource.Editor)
		)
		advanceUntilIdle()

		assertEquals(listOf("text of 11", "fresh words"), comp.state.value.scenes.map { it.markdown })
	}

	@Test
	fun `A buffer update replaces the text of a scene being read`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		bufferCallback.captured(
			SceneBuffer(SceneContent(sceneB, markdown = "fresh words"), dirty = true, source = UpdateSource.Editor)
		)
		advanceUntilIdle()

		assertEquals(listOf("text of 11", "fresh words"), comp.state.value.scenes.map { it.markdown })
	}

	@Test
	fun `A buffer update for another chapter is ignored`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val before = comp.state.value

		bufferCallback.captured(
			SceneBuffer(SceneContent(sceneC, markdown = "elsewhere"), dirty = true, source = UpdateSource.Editor)
		)
		advanceUntilIdle()

		assertEquals(before, comp.state.value)
	}

	@Test
	fun `A scene whose content cannot be read renders empty`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getCurrentSceneContentOrNull(sceneB) } returns null

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf("text of 11", ""), comp.state.value.scenes.map { it.markdown })
	}

	@Test
	fun `An empty group is a chapter with no scenes`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneTree() } returns tree(chapterOne to emptyList())

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(chapterOne.id, comp.state.value.activeChapterId)
		assertTrue(comp.state.value.scenes.isEmpty())
		assertFalse(comp.state.value.isLoading)
	}

	@Test
	fun `Font size changes are written to the reader setting and clamped`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val settings = GlobalSettings(projectsDirectory = "/projects", editorFontSize = 20f)

		comp.increaseFontSize()
		advanceUntilIdle()
		val increased = settingsTransform.captured(settings)
		assertEquals(18f, increased.readerFontSize)
		assertEquals(20f, increased.editorFontSize)

		comp.decreaseFontSize()
		advanceUntilIdle()
		assertEquals(14f, settingsTransform.captured(settings).readerFontSize)

		comp.increaseFontSize()
		advanceUntilIdle()
		assertEquals(
			EDITOR_TEXT_SIZES.last(),
			settingsTransform.captured(settings.copy(readerFontSize = EDITOR_TEXT_SIZES.last())).readerFontSize,
		)

		comp.resetFontSize()
		advanceUntilIdle()
		assertEquals(
			GlobalSettings.DEFAULT_FONT_SIZE,
			settingsTransform.captured(settings.copy(readerFontSize = 30f)).readerFontSize,
		)
	}

	@Test
	fun `The scene headings toggle is persisted and mirrored from settings`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val settings = GlobalSettings(projectsDirectory = "/projects")

		comp.toggleSceneHeadings()
		advanceUntilIdle()
		val toggled = settingsTransform.captured(settings)
		assertTrue(toggled.readerShowSceneHeadings)

		settingsUpdates.emit(toggled.copy(readerFontSize = 22f))
		advanceUntilIdle()

		assertTrue(comp.state.value.showSceneHeadings)
		assertEquals(22f, comp.state.value.fontSize)
	}

	@Test
	fun `The chapter and scene being read survive process death`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showChapter(chapterTwo.id)
		advanceUntilIdle()
		comp.sceneInView(sceneC)

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(initialScene = sceneA, componentContext = restoredContext)
		restoredContext.resume()
		advanceUntilIdle()

		assertEquals(chapterTwo.id, restored.state.value.activeChapterId)
		assertEquals(sceneC.id, restored.state.value.scrollToSceneId)
	}

	@Test
	fun `Close invokes the close callback`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.close()

		assertTrue(closed)
	}
}
