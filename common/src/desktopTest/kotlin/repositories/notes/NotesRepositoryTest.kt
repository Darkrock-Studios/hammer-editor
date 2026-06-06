package repositories.notes

import PROJECT_2_NAME
import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.InvalidNote
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository.Companion.MAX_NOTE_SIZE
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository.Companion.MAX_TAG_SIZE
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import utils.BaseTest
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class NotesRepositoryTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)

	private lateinit var idAllocator: IdAllocator
	private lateinit var syncJournal: SyncJournal
	private lateinit var datasource: NotesDatasource
	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		idAllocator = mockk()
		syncJournal = mockk()
		setupKoin()
	}

	private fun createRepository(): NotesRepository {
		datasource = NotesDatasource(projectDef, ffs, toml)
		return NotesRepository(projectDef, idAllocator, syncJournal, datasource)
	}

	@Test
	fun `Initialize notes repository`() = runTest {
		val repo = createRepository()
	}

	@Test
	fun `Load notes on init`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()

		repo.notesListFlow.test {
			val notes = awaitItem()
			assertEquals(3, notes.size)
		}
	}

	@Test
	fun `Load notes`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()
		repo.loadNotes()

		repo.notesListFlow.test {
			// Skip the initial load from the ctor
			skipItems(1)
			val notes = awaitItem()
			assertEquals(3, notes.size)
		}
	}

	@Test
	fun `Delete note`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs
		val noteId = 12

		val repo = createRepository()
		val path = datasource.getNotePath(noteId).toOkioPath()
		assertTrue(ffs.exists(path))

		repo.deleteNote(noteId)

		assertFalse(ffs.exists(path))

		repo.notesListFlow.test {
			val notes = awaitItem()
			assertEquals(2, notes.size)
			assertNull(notes.find { it.note.id == noteId })
		}
	}

	@Test
	fun `Update note`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.isServerSynchronized() } returns false
		val noteId = 12

		val noteContainer = NoteContainer(
			NoteContent(
				id = noteId,
				created = Instant.fromEpochSeconds(123456),
				content = "Updated note content"
			)
		)

		val repo = createRepository()
		val path = datasource.getNotePath(noteId).toOkioPath()
		assertTrue(ffs.exists(path))

		repo.updateNote(noteContainer.note, false)

		val loadedNote: NoteContainer = ffs.readToml(path, toml)
		assertEquals(noteContainer, loadedNote)

		repo.notesListFlow.test {
			val notes = awaitItem()
			assertEquals(3, notes.size)
			assertEquals(noteContainer, notes.find { it.note.id == noteId })
		}
	}

	@Test
	fun `Create note`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs
		coEvery { syncJournal.isServerSynchronized() } returns false
		coEvery { idAllocator.claimNextId() } returns 15

		val noteText = "New note content"

		val repo = createRepository()
		val result = repo.createNote(noteText)
		assertTrue(isSuccess(result))

		val path = datasource.getNotePath(result.data.id).toOkioPath()
		assertTrue(ffs.exists(path))

		repo.notesListFlow.test {
			val notes = awaitItem()
			assertEquals(4, notes.size)
			assertEquals(result.data, notes.find { it.note.id == result.data.id }?.note)
			assertEquals(noteText, notes.find { it.note.id == result.data.id }?.note?.content)
		}
	}

	@Test
	fun `Create note with tags persists cleaned tags`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs
		coEvery { syncJournal.isServerSynchronized() } returns false
		coEvery { idAllocator.claimNextId() } returns 15

		val repo = createRepository()
		val result = repo.createNote(
			noteText = "Note with tags",
			tags = setOf("  alpha  ", "#beta", "alpha", "with space", "")
		)

		assertTrue(isSuccess(result))
		assertEquals(setOf("alpha", "beta"), result.data.tags)

		val path = datasource.getNotePath(result.data.id).toOkioPath()
		val loaded: NoteContainer = ffs.readToml(path, toml)
		assertEquals(setOf("alpha", "beta"), loaded.note.tags)
	}

	@Test
	fun `Create note rejects tag exceeding MAX_TAG_SIZE`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs
		coEvery { syncJournal.isServerSynchronized() } returns false
		coEvery { idAllocator.claimNextId() } returns 15

		val repo = createRepository()
		val result = repo.createNote(
			noteText = "Note",
			tags = setOf("x".repeat(MAX_TAG_SIZE + 1))
		)

		assertTrue(isFailure(result))
		assertEquals(NoteError.TAG_TOO_LONG, (result.exception as InvalidNote).error)
	}

	@Test
	fun `Update note round-trips tags through TOML`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.isServerSynchronized() } returns false
		val noteId = 12

		val updated = NoteContent(
			id = noteId,
			created = Instant.fromEpochSeconds(123456),
			content = "Updated",
			tags = setOf("foo", "bar"),
		)

		val repo = createRepository()
		repo.updateNote(updated, false)

		val loaded: NoteContainer = ffs.readToml(datasource.getNotePath(noteId).toOkioPath(), toml)
		assertEquals(setOf("foo", "bar"), loaded.note.tags)
	}

	@ParameterizedTest
	@MethodSource("provideCreateFailureTestData")
	fun `Create note failure for invalid name`(noteText: String, error: NoteError) = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs
		coEvery { syncJournal.isServerSynchronized() } returns false
		coEvery { idAllocator.claimNextId() } returns 15

		val repo = createRepository()
		val result = repo.createNote(noteText)
		assertTrue(isFailure(result))
		assertEquals((result.exception as InvalidNote).error, error)

		repo.notesListFlow.test {
			val notes = awaitItem()
			assertEquals(3, notes.size)
		}
	}

	@Test
	fun `Get Note by Id`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val noteId = 12

		val repo = createRepository()
		val note = repo.getNoteById(noteId)

		assertNotNull(note)
		assertEquals("Content of the first note!", note.note.content)
	}

	@Test
	fun `ReId Note`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()
		advanceUntilIdle()

		val oldPath = datasource.getNotePath(14).toOkioPath()
		val newPath = datasource.getNotePath(15).toOkioPath()
		assertTrue(ffs.exists(oldPath))
		assertFalse(ffs.exists(newPath))

		repo.reIdNote(14, 15)

		assertFalse(ffs.exists(oldPath))
		assertTrue(ffs.exists(newPath))
	}

	@Test
	fun `noteContentChangedFlow emits on create update and delete`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs
		coEvery { syncJournal.isServerSynchronized() } returns false
		coEvery { idAllocator.claimNextId() } returns 20

		val repo = createRepository()
		advanceUntilIdle()

		repo.noteContentChangedFlow.test {
			repo.createNote("a note")
			awaitItem()

			repo.updateNote(
				NoteContent(id = 12, created = Instant.fromEpochSeconds(1), content = "x"),
				markForSync = false,
			)
			awaitItem()

			repo.deleteNote(13)
			awaitItem()
		}
	}

	companion object {
		@JvmStatic
		fun provideCreateFailureTestData(): Stream<Arguments> {
			return Stream.of(
				Arguments.of(
					"",
					NoteError.EMPTY
				),
				Arguments.of(
					"x".repeat(MAX_NOTE_SIZE + 1),
					NoteError.TOO_LONG
				),
			)
		}
	}
}