package components.notes

import com.darkrockstudios.apps.hammer.common.components.notes.ViewNoteComponent
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ComponentTest
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ViewNoteComponentTest : ComponentTest() {

	private lateinit var notesRepository: NotesRepository

	private var dismissViewCount = 0
	private var searchedTag: String? = null
	private val addedMenus = mutableListOf<MenuDescriptor>()
	private val removedMenuIds = mutableListOf<String>()

	@BeforeEach
	override fun setup() {
		super.setup()

		notesRepository = mockk(relaxed = true)
		setupComponentKoin(module {
			single { notesRepository } bind NotesRepository::class
		})

		dismissViewCount = 0
		searchedTag = null
		addedMenus.clear()
		removedMenuIds.clear()
	}

	private fun note(id: Int = 1, content: String = "original body", tags: Set<String> = setOf("alpha")) =
		NoteContent(id = id, created = Instant.fromEpochSeconds(1000), content = content, tags = tags)

	private fun newComponent(noteId: Int = 1, ctx: TestComponentContext = context) =
		ViewNoteComponent(
			componentContext = ctx,
			projectDef = projectDef,
			noteId = noteId,
			dismissView = { dismissViewCount++ },
			updateShouldClose = {},
			addMenu = { addedMenus.add(it) },
			removeMenu = { removedMenuIds.add(it) },
			onShowGlobalSearchForTag = { searchedTag = it },
		)

	// Builds the component, stubs the note as already-cached, and drives the lifecycle to
	// RESUMED so onCreate -> loadInitialContent populates state.note synchronously.
	private fun startWith(theNote: NoteContent?): ViewNoteComponent {
		every { notesRepository.findNoteForId(1) } returns theNote
		val comp = newComponent()
		context.resume()
		return comp
	}

	@Test
	fun `loadInitialContent applies the cached note`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "hello", tags = setOf("a", "b")))

		assertEquals("hello", comp.noteText.value)
		assertEquals(setOf("a", "b"), comp.state.value.tags)
		assertEquals(note(content = "hello", tags = setOf("a", "b")), comp.state.value.note)
	}

	@Test
	fun `loadInitialContent falls back to an async load when the note is not cached`() =
		runTest(mainTestDispatcher) {
			// First lookup misses; loadNotes' callback runs, after which the note is available.
			every { notesRepository.findNoteForId(1) } returnsMany listOf(null, note(content = "late"))
			every { notesRepository.loadNotes(any()) } answers { firstArg<(() -> Unit)?>()?.invoke() }

			val comp = newComponent()
			context.resume()

			assertEquals("late", comp.noteText.value)
			assertEquals(note(content = "late"), comp.state.value.note)
		}

	@Test
	fun `beginEdit enters edit mode`() = runTest(mainTestDispatcher) {
		val comp = startWith(note())
		assertFalse(comp.state.value.isEditing)

		comp.beginEdit()

		assertTrue(comp.state.value.isEditing)
	}

	@Test
	fun `isEditingAndDirty is false when not editing`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "body"))
		comp.onContentChanged("body changed by something else")

		assertFalse(comp.isEditingAndDirty())
	}

	@Test
	fun `isEditingAndDirty is true when editing and content differs`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "body"))
		comp.beginEdit()
		comp.onContentChanged("body!")

		assertTrue(comp.isEditingAndDirty())
	}

	@Test
	fun `isEditingAndDirty is false when editing but nothing changed`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "body", tags = setOf("t")))
		comp.beginEdit()

		assertFalse(comp.isEditingAndDirty())
	}

	@Test
	fun `discardEdit restores the original content and tags`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "orig", tags = setOf("a")))
		comp.beginEdit()
		comp.onContentChanged("scratch")
		comp.onTagsChanged(setOf("b"))

		comp.discardEdit()

		assertFalse(comp.state.value.isEditing)
		assertEquals("orig", comp.noteText.value)
		assertEquals(setOf("a"), comp.state.value.tags)
	}

	@Test
	fun `storeNoteUpdate persists the edited content and tags and exits edit mode`() =
		runTest(mainTestDispatcher) {
			val comp = startWith(note(content = "orig", tags = setOf("a")))
			comp.beginEdit()
			comp.onContentChanged("new body")
			comp.onTagsChanged(setOf("x", "y"))

			comp.storeNoteUpdate()

			coVerify { notesRepository.updateNote(note(content = "new body", tags = setOf("x", "y"))) }
			verify { notesRepository.loadNotes() }
			assertEquals("new body", comp.state.value.note?.content)
			assertEquals(setOf("x", "y"), comp.state.value.tags)
			assertFalse(comp.state.value.isEditing)
		}

	@Test
	fun `storeNoteUpdate does nothing when no note is loaded`() = runTest(mainTestDispatcher) {
		val comp = startWith(null)

		comp.storeNoteUpdate()

		coVerify(exactly = 0) { notesRepository.updateNote(any(), any()) }
	}

	@Test
	fun `removeTag removes the tag and persists for a non-editing note`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(tags = setOf("keep", "drop")))

		comp.removeTag("drop")

		coVerify { notesRepository.updateNote(note(tags = setOf("keep"))) }
		assertEquals(setOf("keep"), comp.state.value.tags)
	}

	@Test
	fun `removeTag is a no-op while editing`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(tags = setOf("keep", "drop")))
		comp.beginEdit()

		comp.removeTag("drop")

		coVerify(exactly = 0) { notesRepository.updateNote(any(), any()) }
	}

	@Test
	fun `removeTag is a no-op for a tag the note does not have`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(tags = setOf("keep")))

		comp.removeTag("absent")

		coVerify(exactly = 0) { notesRepository.updateNote(any(), any()) }
	}

	@Test
	fun `deleteNote deletes, reloads, and dismisses the view`() = runTest(mainTestDispatcher) {
		val comp = startWith(note())

		comp.deleteNote(1)

		coVerify { notesRepository.deleteNote(1) }
		verify { notesRepository.loadNotes() }
		assertEquals(1, dismissViewCount)
	}

	@Test
	fun `confirmDelete and dismissConfirmDelete toggle the flag`() = runTest(mainTestDispatcher) {
		val comp = startWith(note())

		comp.confirmDelete()
		assertTrue(comp.state.value.confirmDelete)

		comp.dismissConfirmDelete()
		assertFalse(comp.state.value.confirmDelete)
	}

	@Test
	fun `confirmClose closes immediately when not dirty`() = runTest(mainTestDispatcher) {
		val comp = startWith(note())

		comp.confirmClose()

		assertFalse(comp.state.value.confirmClose)
		assertEquals(1, dismissViewCount)
	}

	@Test
	fun `confirmClose raises the dialog when editing and dirty`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "orig"))
		comp.beginEdit()
		comp.onContentChanged("dirty")

		comp.confirmClose()

		assertTrue(comp.state.value.confirmClose)
		assertEquals(0, dismissViewCount)
	}

	@Test
	fun `confirmDiscard raises the dialog when dirty and discards when clean`() =
		runTest(mainTestDispatcher) {
			val comp = startWith(note(content = "orig"))
			comp.beginEdit()
			comp.onContentChanged("dirty")

			comp.confirmDiscard()
			assertTrue(comp.state.value.confirmDiscard)

			// Once clean again, confirmDiscard discards directly rather than re-prompting.
			comp.onContentChanged("orig")
			comp.cancelDiscard()
			comp.confirmDiscard()
			assertFalse(comp.state.value.confirmDiscard)
			assertFalse(comp.state.value.isEditing)
		}

	@Test
	fun `showGlobalSearchForTag forwards the tag to the parent`() = runTest(mainTestDispatcher) {
		val comp = startWith(note())

		comp.showGlobalSearchForTag("magic")

		assertEquals("magic", searchedTag)
	}

	@Test
	fun `a back press while editing and dirty raises the discard dialog`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "orig"))
		comp.beginEdit()
		comp.onContentChanged("dirty")

		val handled = context.back()

		assertTrue(handled)
		assertTrue(comp.state.value.confirmDiscard)
	}

	@Test
	fun `a back press while editing and clean discards the edit`() = runTest(mainTestDispatcher) {
		val comp = startWith(note(content = "orig"))
		comp.beginEdit()

		context.back()

		assertFalse(comp.state.value.isEditing)
		assertFalse(comp.state.value.confirmDiscard)
	}

	@Test
	fun `the entry menu is added on start and removed on stop`() = runTest(mainTestDispatcher) {
		val comp = startWith(note())
		assertEquals(1, addedMenus.size)
		assertTrue(comp.state.value.menuItems.isNotEmpty())

		context.stop()

		assertTrue(removedMenuIds.isNotEmpty())
		assertTrue(comp.state.value.menuItems.isEmpty())
	}

	@Test
	fun `an in-progress edit survives process death and is restored`() = runTest(mainTestDispatcher) {
		// Edit, but do not save.
		val comp = startWith(note(content = "orig", tags = setOf("a")))
		comp.beginEdit()
		comp.onContentChanged("half-written thought")
		comp.onTagsChanged(setOf("wip"))

		// Simulate process death: serialize, then recreate from the saved container.
		val restoredContext = context.saveAndRecreate()
		// The stored note is unchanged on disk; the restored draft must win over it.
		every { notesRepository.findNoteForId(1) } returns note(content = "orig", tags = setOf("a"))
		val restored = newComponent(ctx = restoredContext)
		restoredContext.resume()

		assertTrue(restored.state.value.isEditing)
		assertEquals("half-written thought", restored.noteText.value)
		assertEquals(setOf("wip"), restored.state.value.tags)
	}

	@Test
	fun `a clean view is not treated as a restored edit after process death`() = runTest(mainTestDispatcher) {
		startWith(note(content = "orig"))
		// Never entered edit mode.

		val restoredContext = context.saveAndRecreate()
		every { notesRepository.findNoteForId(1) } returns note(content = "orig")
		val restored = newComponent(ctx = restoredContext)
		restoredContext.resume()

		assertFalse(restored.state.value.isEditing)
		assertEquals("orig", restored.noteText.value)
	}
}
