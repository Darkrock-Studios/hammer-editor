package components.notes

import com.darkrockstudios.apps.hammer.common.components.notes.BrowseNotesComponent
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseNotesComponentTest : ComponentTest() {

	private lateinit var notesRepository: NotesRepository
	private lateinit var tagIndexService: TagIndexService

	private lateinit var notesFlow: MutableStateFlow<List<NoteContainer>>
	private lateinit var tagIndexFlow: MutableStateFlow<TagIndex>

	private var createShownCount = 0
	private var viewedNoteId: Int? = null

	@BeforeEach
	override fun setup() {
		super.setup()

		notesRepository = mockk(relaxed = true)
		tagIndexService = mockk(relaxed = true)

		notesFlow = MutableStateFlow(emptyList())
		tagIndexFlow = MutableStateFlow(TagIndex.EMPTY)
		every { notesRepository.notesListFlow } returns notesFlow
		every { tagIndexService.tagIndex } returns tagIndexFlow
		every { tagIndexService.getRankedTags(TaggedEntityType.Note) } returns emptyList()

		setupKoin(module {
			single { notesRepository } bind NotesRepository::class
			single { tagIndexService } bind TagIndexService::class
		})

		createShownCount = 0
		viewedNoteId = null
	}

	private fun newComponent() = BrowseNotesComponent(
		componentContext = context,
		projectDef = projectDef,
		onShowCreate = { createShownCount++ },
		onViewNote = { viewedNoteId = it },
	)

	private fun container(id: Int, createdAt: Long) =
		NoteContainer(NoteContent(id = id, created = Instant.fromEpochSeconds(createdAt), content = "n$id"))

	@Test
	fun `notes from the repository flow are exposed sorted by created descending`() =
		runTest(mainTestDispatcher) {
			notesFlow.value = listOf(
				container(id = 1, createdAt = 100),
				container(id = 2, createdAt = 300),
				container(id = 3, createdAt = 200),
			)

			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			assertEquals(listOf(2, 3, 1), comp.state.value.notes.map { it.id })
		}

	@Test
	fun `loadNotes is requested on create`() = runTest(mainTestDispatcher) {
		newComponent()
		context.resume()
		advanceUntilIdle()

		verify { notesRepository.loadNotes() }
	}

	@Test
	fun `rankedTags reflects the note tags from the index`() = runTest(mainTestDispatcher) {
		val ranked = listOf(TagCount("hero", 3), TagCount("villain", 1))
		every { tagIndexService.getRankedTags(TaggedEntityType.Note) } returns ranked

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(ranked, comp.rankedTags.value)
	}

	@Test
	fun `viewNote forwards the id to the parent`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.viewNote(42)

		assertEquals(42, viewedNoteId)
	}

	@Test
	fun `showCreate forwards to the parent`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.showCreate()

		assertEquals(1, createShownCount)
	}
}
