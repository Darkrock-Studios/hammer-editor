package components.notes

import com.darkrockstudios.apps.hammer.common.components.notes.CreateNoteComponent
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.notesrepository.InvalidNote
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import io.mockk.coEvery
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

class CreateNoteComponentTest : ComponentTest() {

	private lateinit var notesRepository: NotesRepository

	private var dismissCreateCount = 0

	@BeforeEach
	override fun setup() {
		super.setup()

		notesRepository = mockk(relaxed = true)
		setupComponentKoin(module {
			single { notesRepository } bind NotesRepository::class
		})

		dismissCreateCount = 0
	}

	private fun newComponent(ctx: TestComponentContext = context) =
		CreateNoteComponent(
			componentContext = ctx,
			projectDef = projectDef,
			dismissCreate = { dismissCreateCount++ },
			updateShouldClose = {},
		)

	private fun started(): CreateNoteComponent {
		val comp = newComponent()
		context.resume()
		return comp
	}

	private fun noteContent(content: String) =
		NoteContent(id = 1, created = Instant.fromEpochSeconds(1), content = content)

	@Test
	fun `createNote dismisses, reloads, and returns NONE on success`() = runTest(mainTestDispatcher) {
		coEvery { notesRepository.createNote("body", emptySet()) } returns CResult.success(noteContent("body"))
		val comp = started()

		val error = comp.createNote("body", emptySet())

		assertEquals(NoteError.NONE, error)
		assertEquals(1, dismissCreateCount)
		verify { notesRepository.loadNotes() }
	}

	@Test
	fun `createNote returns the validation error and does not dismiss on failure`() =
		runTest(mainTestDispatcher) {
			coEvery { notesRepository.createNote("", emptySet()) } returns
				CResult.failure(InvalidNote(NoteError.EMPTY))
			val comp = started()

			val error = comp.createNote("", emptySet())

			assertEquals(NoteError.EMPTY, error)
			assertEquals(0, dismissCreateCount)
			verify(exactly = 0) { notesRepository.loadNotes() }
		}

	@Test
	fun `onTextChanged updates the note text`() = runTest(mainTestDispatcher) {
		val comp = started()

		comp.onTextChanged("hello")

		assertEquals("hello", comp.noteText.value)
	}

	@Test
	fun `clearText empties the note text`() = runTest(mainTestDispatcher) {
		val comp = started()
		comp.onTextChanged("hello")

		comp.clearText()

		assertEquals("", comp.noteText.value)
	}

	@Test
	fun `closeCreate dismisses immediately when the text is blank`() = runTest(mainTestDispatcher) {
		val comp = started()

		comp.closeCreate()

		assertEquals(1, dismissCreateCount)
		assertFalse(comp.state.value.confirmDiscard)
	}

	@Test
	fun `closeCreate raises the discard dialog when there is text`() = runTest(mainTestDispatcher) {
		val comp = started()
		comp.onTextChanged("unsaved")

		comp.closeCreate()

		assertEquals(0, dismissCreateCount)
		assertTrue(comp.state.value.confirmDiscard)
	}

	@Test
	fun `confirmDiscard and cancelDiscard toggle the flag`() = runTest(mainTestDispatcher) {
		val comp = started()

		comp.confirmDiscard()
		assertTrue(comp.state.value.confirmDiscard)

		comp.cancelDiscard()
		assertFalse(comp.state.value.confirmDiscard)
	}

	@Test
	fun `the back handler is enabled only while there is text`() = runTest(mainTestDispatcher) {
		val comp = started()
		assertFalse(context.backDispatcher.isEnabled)

		comp.onTextChanged("typing")
		assertTrue(context.backDispatcher.isEnabled)

		comp.clearText()
		assertFalse(context.backDispatcher.isEnabled)
	}

	@Test
	fun `a back press raises the discard dialog`() = runTest(mainTestDispatcher) {
		val comp = started()
		comp.onTextChanged("unsaved")

		val handled = context.back()

		assertTrue(handled)
		assertTrue(comp.state.value.confirmDiscard)
	}

	@Test
	fun `the draft text survives process death`() = runTest(mainTestDispatcher) {
		val comp = started()
		comp.onTextChanged("half a thought")

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(restoredContext)
		restoredContext.resume()

		assertEquals("half a thought", restored.noteText.value)
	}
}
