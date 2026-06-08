package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ClientNoteSynchronizerTest : BaseTest() {

	private val projectDef = ProjectDef(name = "Test", path = HPath("/projects/Test", "Test", false))
	private val strRes: StrRes = TestStrRes()

	@MockK
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var notesRepository: NotesRepository

	private fun noteContainer(id: Int, content: String = "body", tags: Set<String> = setOf("a")) =
		NoteContainer(NoteContent(id = id, created = Instant.fromEpochSeconds(100), content = content, tags = tags))

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				scoped { notesRepository }
			}
		})
	}

	private fun newSynchronizer() = ClientNoteSynchronizer(
		projectDef = projectDef,
		serverProjectApi = serverProjectApi,
		projectMetadataDatasource = projectMetadataDatasource,
		strRes = strRes,
	)

	@Test
	fun `getEntityType is Note`() {
		assertEquals(EntityType.Note, newSynchronizer().getEntityType())
	}

	@Test
	fun `ownsEntity reflects whether the note exists`() = runTest {
		every { notesRepository.notesListFlow } returns MutableStateFlow(listOf(noteContainer(5)))
		val sync = newSynchronizer()
		assertTrue(sync.ownsEntity(5))
		assertFalse(sync.ownsEntity(99))
	}

	@Test
	fun `createEntityForId maps the note fields`() = runTest {
		coEvery { notesRepository.getNoteById(5) } returns noteContainer(5, content = "hello", tags = setOf("x"))

		val entity = newSynchronizer().createEntityForId(5)

		assertEquals(5, entity.id)
		assertEquals("hello", entity.content)
		assertEquals(setOf("x"), entity.tags)
		assertEquals(Instant.fromEpochSeconds(100), entity.created)
	}

	@Test
	fun `createEntityForId throws when the note is missing`() = runTest {
		coEvery { notesRepository.getNoteById(99) } returns null
		assertFailsWith<IllegalStateException> { newSynchronizer().createEntityForId(99) }
	}

	@Test
	fun `storeEntity writes the server note without marking it for sync`() = runTest {
		val serverEntity = ApiProjectEntity.NoteEntity(
			id = 5,
			content = "server body",
			created = Instant.fromEpochSeconds(200),
			tags = setOf("srv"),
		)

		val result = newSynchronizer().storeEntity(serverEntity, "sync-1", {})

		assertTrue(result)
		coVerify {
			notesRepository.updateNote(
				NoteContent(
					id = 5,
					created = Instant.fromEpochSeconds(200),
					content = "server body",
					tags = setOf("srv")
				),
				false,
			)
		}
	}

	@Test
	fun `reIdEntity delegates to the repository`() = runTest {
		newSynchronizer().reIdEntity(oldId = 3, newId = 9)
		coVerify { notesRepository.reIdNote(3, 9) }
	}

	@Test
	fun `deleteEntityLocal deletes the note and logs`() = runTest {
		val logs = mutableListOf<SyncLogMessage>()

		newSynchronizer().deleteEntityLocal(7) { logs.add(it) }

		coVerify { notesRepository.deleteNote(7) }
		assertEquals(1, logs.size)
		assertEquals(SyncLogLevel.INFO, logs.first().level)
	}
}
