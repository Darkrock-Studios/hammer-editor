package components.notes

import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.components.notes.NotesComponent
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckService
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
import kotlin.test.assertNotNull
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
		every { notesRepository.notesListFlow } returns MutableStateFlow(listOf(NoteContainer(note)))
		every { notesRepository.findNoteForId(any()) } returns note

		tagIndexService = mockk(relaxed = true)
		every { tagIndexService.tagIndex } returns MutableStateFlow(TagIndex.EMPTY)

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { notesRepository }
				scoped { tagIndexService }
			}
			single<ProjectSpellCheckService> {
				mockk(relaxed = true) {
					every { spellCheckAllowed } returns emptyFlow()
				}
			}
		})

		shouldCloseUpdates = 0
		searchedTag = null
	}

	private val registry = ActionRegistry()
	private fun newComponent() = NotesComponent(
		componentContext = context,
		projectDef = projectDef,
		updateShouldClose = { shouldCloseUpdates++ },
		actions = registry,
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

	@Test
	fun `discardUnsaved reverts a dirty note edit`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewNote(1)
		advanceUntilIdle()

		val destination = assertIs<Notes.Destination.ViewNoteDestination>(comp.stack.value.active.instance)
		destination.component.beginEdit()
		destination.component.onContentChanged("changed body")
		advanceUntilIdle()

		comp.discardUnsaved(CloseConfirm.Notes)
		advanceUntilIdle()

		assertEquals(emptySet(), comp.shouldConfirmClose())
		assertEquals(note.content, destination.component.noteText.value)
	}

	@Test
	fun `discardUnsaved pops an open create note screen`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showCreateNote()
		advanceUntilIdle()

		comp.discardUnsaved(CloseConfirm.Notes)
		advanceUntilIdle()

		assertIs<Notes.Destination.BrowseNotesDestination>(comp.stack.value.active.instance)
		assertEquals(emptySet(), comp.shouldConfirmClose())
	}

	@Test
	fun `discardUnsaved clears a dirty note edit beneath a create screen`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewNote(1)
		advanceUntilIdle()
		val view = assertIs<Notes.Destination.ViewNoteDestination>(comp.stack.value.active.instance)
		view.component.beginEdit()
		view.component.onContentChanged("changed body")
		comp.showCreateNote()
		advanceUntilIdle()
		assertEquals(setOf(CloseConfirm.Notes), comp.shouldConfirmClose())

		comp.discardUnsaved(CloseConfirm.Notes)
		advanceUntilIdle()

		assertIs<Notes.Destination.ViewNoteDestination>(comp.stack.value.active.instance)
		assertFalse(view.component.state.value.isEditing)
		assertEquals(emptySet(), comp.shouldConfirmClose())
	}

	@Test
	fun `discardUnsaved ignores other confirmation types`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showCreateNote()
		advanceUntilIdle()

		comp.discardUnsaved(CloseConfirm.Scenes)
		advanceUntilIdle()

		assertIs<Notes.Destination.CreateNoteDestination>(comp.stack.value.active.instance)
	}

	private fun noteWithId(id: Int) =
		NoteContainer(NoteContent(id = id, created = Instant.fromEpochSeconds(1000L + id), content = "note $id"))

	private fun givenNotes(vararg ids: Int): MutableStateFlow<List<NoteContainer>> {
		val notes = MutableStateFlow(ids.map(::noteWithId))
		every { notesRepository.notesListFlow } returns notes
		every { notesRepository.findNoteForId(any()) } answers {
			notes.value.find { it.note.id == firstArg<Int>() }?.note
		}
		return notes
	}

	private fun browse(comp: NotesComponent) =
		assertNotNull(comp.stack.value.items.firstNotNullOfOrNull {
			(it.instance as? Notes.Destination.BrowseNotesDestination)?.component
		})

	@Test
	fun `Mod+N opens a new note`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.N)))
		advanceUntilIdle()

		assertIs<Notes.Destination.CreateNoteDestination>(comp.stack.value.active.instance)
	}

	@Test
	fun `Mod+PageDown opens the next note in the order the list shows`() = runTest(mainTestDispatcher) {
		givenNotes(1, 2, 3)
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		browse(comp).onVisibleOrderChanged(listOf(3, 1, 2))
		comp.showViewNote(1)
		advanceUntilIdle()

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.PageDown)))
		advanceUntilIdle()

		assertEquals(Notes.Config.ViewNoteConfig(2), comp.stack.value.active.configuration)
		assertEquals(2, comp.stack.value.items.size)
	}

	@Test
	fun `Mod+PageUp opens the previous note and stops at the first`() = runTest(mainTestDispatcher) {
		givenNotes(1, 2, 3)
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		browse(comp).onVisibleOrderChanged(listOf(3, 1, 2))
		comp.showViewNote(1)
		advanceUntilIdle()

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.PageUp)))
		advanceUntilIdle()

		assertEquals(Notes.Config.ViewNoteConfig(3), comp.stack.value.active.configuration)
		assertFalse(registry.dispatch(KeyChord.mod(ChordKey.PageUp)))
	}

	@Test
	fun `next note follows the newest first order until the list reports one`() = runTest(mainTestDispatcher) {
		givenNotes(1, 2, 3)
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewNote(3)
		advanceUntilIdle()

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.PageDown)))
		advanceUntilIdle()

		assertEquals(Notes.Config.ViewNoteConfig(2), comp.stack.value.active.configuration)
	}

	@Test
	fun `next note is not live while browsing`() = runTest(mainTestDispatcher) {
		givenNotes(1, 2, 3)
		newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(registry.isLive(HammerActions.ItemNext.id))
		assertFalse(registry.dispatch(KeyChord.mod(ChordKey.PageDown)))
	}

	@Test
	fun `next note is not live over a dirty edit`() = runTest(mainTestDispatcher) {
		givenNotes(1, 2, 3)
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewNote(3)
		advanceUntilIdle()
		val view = assertIs<Notes.Destination.ViewNoteDestination>(comp.stack.value.active.instance)
		view.component.beginEdit()
		view.component.onContentChanged("changed body")

		assertFalse(registry.dispatch(KeyChord.mod(ChordKey.PageDown)))
		assertEquals(Notes.Config.ViewNoteConfig(3), comp.stack.value.active.configuration)
	}

	@Test
	fun `Mod+W closes a blank new note`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		comp.showCreateNote()
		advanceUntilIdle()

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.W)))
		advanceUntilIdle()

		assertIs<Notes.Destination.BrowseNotesDestination>(comp.stack.value.active.instance)
	}

	@Test
	fun `Mod+W on a new note with text asks to discard it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		comp.showCreateNote()
		advanceUntilIdle()
		val create = assertIs<Notes.Destination.CreateNoteDestination>(comp.stack.value.active.instance).component
		create.onTextChanged("half a thought")

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.W)))
		advanceUntilIdle()

		assertTrue(create.state.value.confirmDiscard)
		assertIs<Notes.Destination.CreateNoteDestination>(comp.stack.value.active.instance)
	}

	@Test
	fun `a note added while the list is not reporting can be stepped to and from`() = runTest(mainTestDispatcher) {
		val notes = givenNotes(1, 2, 3)
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		browse(comp).onVisibleOrderChanged(listOf(3, 1, 2))
		comp.showViewNote(3)
		advanceUntilIdle()

		notes.value = notes.value + noteWithId(4)
		advanceUntilIdle()

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.PageUp)))
		advanceUntilIdle()
		assertEquals(Notes.Config.ViewNoteConfig(4), comp.stack.value.active.configuration)

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.PageDown)))
		advanceUntilIdle()
		assertEquals(Notes.Config.ViewNoteConfig(3), comp.stack.value.active.configuration)
	}

	@Test
	fun `next note skips a note deleted while the list is not reporting`() = runTest(mainTestDispatcher) {
		val notes = givenNotes(1, 2, 3)
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		browse(comp).onVisibleOrderChanged(listOf(3, 1, 2))
		comp.showViewNote(3)
		advanceUntilIdle()

		notes.value = notes.value.filterNot { it.note.id == 1 }
		advanceUntilIdle()

		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.PageDown)))
		advanceUntilIdle()
		assertEquals(Notes.Config.ViewNoteConfig(2), comp.stack.value.active.configuration)
	}
}
