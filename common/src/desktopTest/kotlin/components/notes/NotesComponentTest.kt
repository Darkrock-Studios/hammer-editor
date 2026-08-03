package components.notes

import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.components.notes.NotesComponent
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotesComponentTest : ComponentTest() {

	private lateinit var notesRepository: NotesRepository
	private lateinit var tagIndexService: TagIndexService

	private var shouldCloseUpdates = 0
	private var searchedTag: String? = null

	private val note = NoteContent(
		id = 1,
		created = Instant.fromEpochSeconds(1000),
		content = "original body",
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		notesRepository = mockk(relaxed = true)
		every { notesRepository.notesListFlow } returns MutableStateFlow(emptyList<NoteContainer>())
		every { notesRepository.findNoteForId(any()) } returns note

		tagIndexService = mockk(relaxed = true)
		every { tagIndexService.tagIndex } returns MutableStateFlow(TagIndex.EMPTY)

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { notesRepository }
				scoped { tagIndexService }
			}
			single<ProjectSpellCheckRepository> {
				mockk(relaxed = true) {
					every { spellCheckAllowed } returns emptyFlow()
				}
			}
		})

		shouldCloseUpdates = 0
		searchedTag = null
	}

	private fun newComponent() = NotesComponent(
		componentContext = context,
		projectDef = projectDef,
		updateShouldClose = { shouldCloseUpdates++ },
		addMenu = {},
		removeMenu = {},
		onShowGlobalSearchForTag = { searchedTag = it },
	)

	@Test
	fun `the browse screen is the initial destination`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertIs<Notes.Destination.BrowseNotesDestination>(comp.stack.value.active.instance)
		assertTrue(comp.isAtRoot())
		assertEquals(emptySet(), comp.shouldConfirmClose())
	}

	@Test
	fun `showViewNote pushes the view note destination`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showViewNote(1)
		advanceUntilIdle()

		val destination = assertIs<Notes.Destination.ViewNoteDestination>(comp.stack.value.active.instance)
		assertEquals(note, destination.component.state.value.note)
		assertFalse(comp.isAtRoot())
	}

	@Test
	fun `showCreateNote pushes the create destination which always requires close confirmation`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.showCreateNote()
			advanceUntilIdle()

			assertIs<Notes.Destination.CreateNoteDestination>(comp.stack.value.active.instance)
			assertEquals(setOf(CloseConfirm.Notes), comp.shouldConfirmClose())
		}

	@Test
	fun `showBrowse pops back to the browse root`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewNote(1)
		comp.showCreateNote()
		advanceUntilIdle()

		comp.showBrowse()
		advanceUntilIdle()

		assertIs<Notes.Destination.BrowseNotesDestination>(comp.stack.value.active.instance)
		assertEquals(1, comp.stack.value.items.size)
		assertTrue(comp.isAtRoot())
	}

	@Test
	fun `onBack pops the top destination`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewNote(1)
		advanceUntilIdle()

		comp.onBack()
		advanceUntilIdle()

		assertIs<Notes.Destination.BrowseNotesDestination>(comp.stack.value.active.instance)
		assertTrue(comp.isAtRoot())
	}

	@Test
	fun `viewing a note without editing does not require close confirmation`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.showViewNote(1)
			advanceUntilIdle()

			assertEquals(emptySet(), comp.shouldConfirmClose())
		}

	@Test
	fun `a dirty note edit requires close confirmation`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewNote(1)
		advanceUntilIdle()

		val destination = assertIs<Notes.Destination.ViewNoteDestination>(comp.stack.value.active.instance)
		destination.component.beginEdit()
		destination.component.onContentChanged("changed body")
		advanceUntilIdle()

		assertEquals(setOf(CloseConfirm.Notes), comp.shouldConfirmClose())
	}

	@Test
	fun `stack navigation notifies updateShouldClose`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val baseline = shouldCloseUpdates

		comp.showViewNote(1)
		advanceUntilIdle()

		assertTrue(shouldCloseUpdates > baseline)
	}
}
