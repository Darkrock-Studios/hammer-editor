package components.storyeditor.drafts

import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftCompareComponent
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DraftCompareComponentTest : ComponentTest() {

	private lateinit var draftsRepository: SceneDraftRepository
	private lateinit var sceneEditor: SceneEditorService

	private var cancelled = false
	private var returnedToEditor = false

	private val sceneItem
		get() = SceneItem(projectDef, SceneItem.Type.Scene, id = 7, name = "Chapter One", order = 0)

	private val draftDef
		get() = DraftDef(
			id = 2,
			sceneId = 7,
			draftTimestamp = Instant.DISTANT_PAST,
			draftName = "First Draft",
		)

	private val currentContent get() = SceneContent(scene = sceneItem, markdown = "current words")
	private val draftContent get() = SceneContent(scene = sceneItem, markdown = "draft words")

	@BeforeEach
	override fun setup() {
		super.setup()

		cancelled = false
		returnedToEditor = false

		sceneEditor = mockk(relaxed = true)
		every { sceneEditor.loadSceneBuffer(any()) } returns SceneBuffer(
			content = currentContent,
			dirty = false,
			source = UpdateSource.Repository,
		)

		draftsRepository = mockk(relaxed = true)
		every { draftsRepository.loadDraft(any(), any()) } returns draftContent

		setupComponentKoin(module {
			scope<ProjectDefScope> {
				scoped { draftsRepository }
				scoped { sceneEditor }
			}
		})
	}

	private fun newComponent() = DraftCompareComponent(
		componentContext = context,
		sceneItem = sceneItem,
		draftDef = draftDef,
		cancelCompare = { cancelled = true },
		backToEditor = { returnedToEditor = true },
	)

	@Test
	fun `Both sides load on create`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(currentContent, comp.state.value.sceneContent)
		assertEquals(draftContent, comp.state.value.draftContent)
	}

	@Test
	fun `Submitting both texts computes a diff`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.submitDraftText("The quick brown fox")
		comp.onCurrentTextChanged("The quick red fox")
		advanceUntilIdle()

		val diff = comp.state.value.diffResult
		assertNotNull(diff)
		assertTrue(
			diff.leftSpans.isNotEmpty() && diff.rightSpans.isNotEmpty(),
			"Diff should mark the brown/red word change on both sides",
		)
	}

	@Test
	fun `Picking the draft overwrites the scene and returns to the editor`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.pickDraft()

		verify { sceneEditor.onContentChanged(draftContent, UpdateSource.Drafts) }
		assertTrue(returnedToEditor)
	}

	@Test
	fun `Picking merged without edits falls back to the current content`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.pickMerged()

		verify {
			sceneEditor.onContentChanged(
				match { it.markdown == "current words" },
				UpdateSource.Drafts,
			)
		}
		assertTrue(returnedToEditor)
	}

	@Test
	fun `Cancel abandons the comparison`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.cancel()
		assertTrue(cancelled)
	}
}
