package components.storyeditor.drafts

import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftsListComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DraftsListComponentTest : ComponentTest() {

	private lateinit var draftsRepository: SceneDraftRepository

	private var closed = false
	private val comparisons = mutableListOf<Pair<SceneItem, DraftDef>>()

	private val sceneItem
		get() = SceneItem(projectDef, SceneItem.Type.Scene, id = 7, name = "Chapter One", order = 0)

	private fun draftDef(id: Int) = DraftDef(
		id = id,
		sceneId = sceneItem.id,
		draftTimestamp = Instant.DISTANT_PAST,
		draftName = "Draft $id",
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		closed = false
		comparisons.clear()
		draftsRepository = mockk(relaxed = true)

		setupComponentKoin(module {
			scope<ProjectDefScope> {
				scoped { draftsRepository }
			}
		})
	}

	private fun newComponent() = DraftsListComponent(
		componentContext = context,
		sceneItem = sceneItem,
		closeDrafts = { closed = true },
		compareDraft = { scene, draft -> comparisons.add(scene to draft) },
	)

	@Test
	fun `Drafts for the scene load into state`() = runTest(mainTestDispatcher) {
		val drafts = listOf(draftDef(1), draftDef(2))
		every { draftsRepository.findDrafts(sceneItem.id) } returns drafts

		val comp = newComponent()
		context.resume()

		comp.loadDrafts()
		advanceUntilIdle()

		assertEquals(drafts, comp.state.value.drafts)
	}

	@Test
	fun `Selecting a draft requests a comparison`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		val draft = draftDef(3)
		comp.selectDraft(draft)

		assertEquals(listOf(sceneItem to draft), comparisons)
	}

	@Test
	fun `Cancel closes the drafts list`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.cancel()
		assertTrue(closed)
	}
}
