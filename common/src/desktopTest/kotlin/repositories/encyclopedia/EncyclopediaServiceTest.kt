package repositories.encyclopedia

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndex
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import createProject
import getProjectDef
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the cross-cutting side-effects the service applies on top of the repository:
 * statistics dirty-marking and reference-index purging. These were previously baked into
 * the repository and silently untested.
 */
class EncyclopediaServiceTest : BaseTest() {

	private val projDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	@MockK
	lateinit var idAllocator: IdAllocator

	@MockK
	lateinit var externalFileIo: ExternalFileIo

	@MockK
	lateinit var syncJournal: SyncJournal

	@MockK
	lateinit var statisticsRepository: StatisticsRepository

	private lateinit var datasource: EncyclopediaDatasource
	private lateinit var referenceIndexDatasource: ReferenceIndexDatasource
	private lateinit var referenceIndexRepository: ReferenceIndexRepository

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		setupKoin()

		every { syncJournal.isServerSynchronized() } returns false
		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		createProject(fileSystem, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
	}

	private fun createService(): EncyclopediaService {
		datasource = EncyclopediaDatasource(
			projectDef = projDef,
			toml = toml,
			fileSystem = fileSystem,
			externalFileIo = externalFileIo,
		)
		referenceIndexDatasource = ReferenceIndexDatasource(fileSystem, toml, projDef)
		referenceIndexRepository = ReferenceIndexRepository(projDef, referenceIndexDatasource)
		val repository = EncyclopediaRepository(
			projectDef = projDef,
			idAllocator = idAllocator,
			datasource = datasource,
			syncJournal = syncJournal,
		)
		return EncyclopediaService(
			repository = repository,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = referenceIndexRepository,
		)
	}

	@Test
	fun `createEntry marks statistics dirty`() = runTest {
		coEvery { idAllocator.claimNextId() } returns 10

		val service = createService()
		val result = service.createEntry(
			name = "New Entry",
			type = EntryType.PERSON,
			text = "Some text",
			tags = emptySet(),
			imagePath = null,
		)

		assertEquals(EntryError.NONE, result.error)
		coVerify { statisticsRepository.markDirty() }
	}

	@Test
	fun `createEntry with invalid name does not mark statistics dirty`() = runTest {
		val service = createService()
		val result = service.createEntry(
			name = "",
			type = EntryType.PERSON,
			text = "Some text",
			tags = emptySet(),
			imagePath = null,
		)

		assertEquals(EntryError.NAME_TOO_SHORT, result.error)
		coVerify(exactly = 0) { statisticsRepository.markDirty() }
	}

	@Test
	fun `updateEntry marks statistics dirty`() = runTest {
		val service = createService()
		val result = service.updateEntry(
			oldEntryDef = entry1().toDef(projDef),
			name = "A new name",
			text = entry1().text,
			tags = entry1().tags,
			excludeFromDictionary = false,
		)

		assertEquals(EntryError.NONE, result.error)
		coVerify { statisticsRepository.markDirty() }
	}

	@Test
	fun `updateEntry with invalid name does not mark statistics dirty`() = runTest {
		val service = createService()
		val result = service.updateEntry(
			oldEntryDef = entry1().toDef(projDef),
			name = "",
			text = entry1().text,
			tags = entry1().tags,
			excludeFromDictionary = false,
		)

		assertEquals(EntryError.NAME_TOO_SHORT, result.error)
		coVerify(exactly = 0) { statisticsRepository.markDirty() }
	}

	@Test
	fun `deleteEntry marks statistics dirty and purges the reference index`() = runTest {
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs

		val service = createService()
		referenceIndexDatasource.saveIndex(
			ReferenceIndex(
				isDirty = false,
				entryToScenes = mapOf(
					entry1().id to setOf(100, 200),
					99 to setOf(100),
				),
			)
		)
		referenceIndexRepository.loadIndex()

		val deleted = service.deleteEntry(entry1().toDef(projDef))
		assertTrue(deleted)

		coVerify { statisticsRepository.markDirty() }

		val saved = referenceIndexDatasource.loadIndex()
		assertEquals(null, saved?.entryToScenes?.get(entry1().id))
		assertEquals(setOf(100), saved?.entryToScenes?.get(99))
	}
}
