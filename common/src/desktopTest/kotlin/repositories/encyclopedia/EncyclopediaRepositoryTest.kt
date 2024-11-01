package repositories.encyclopedia

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository.Companion.MAX_NAME_SIZE
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncyclopediaRepositoryTest : BaseTest() {

	private val projDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	@MockK
	lateinit var idRepository: IdRepository

	@MockK
	lateinit var externalFileIo: ExternalFileIo

	@MockK
	lateinit var syncDataRepository: SyncDataRepository

	lateinit var datasource: EncyclopediaDatasource

	private lateinit var fileSystem: FakeFileSystem
	lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		setupKoin()

		every { syncDataRepository.isServerSynchronized() } returns false
		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		createProject(fileSystem, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
	}

	private fun createRepository(): EncyclopediaRepository {
		datasource = EncyclopediaDatasource(
			projectDef = projDef,
			toml = toml,
			fileSystem = fileSystem,
			externalFileIo = externalFileIo,
		)
		return EncyclopediaRepository(
			projectDef = projDef,
			idRepository = idRepository,
			datasource = datasource,
			syncDataRepository = syncDataRepository
		)
	}

	@Test
	fun `Update Entry Name - Valid`() = runTest {
		val entry = entry1()
		val origDef = entry.toDef(projDef)

		val newValidName = "A new name"
		val newEntry = entry.copy(
			name = newValidName
		)

		val repo = createRepository()

		val result = repo.updateEntry(
			oldEntryDef = origDef,
			name = newValidName,
			text = entry.text,
			tags = entry.tags
		)


		assertEquals(EntryError.NONE, result.error)
		assertEquals(newEntry, result.instance?.entry)

		val newPath = datasource.getEntryPath(newEntry).toOkioPath()
		assertTrue(fileSystem.exists(newPath))

		val writtenEntry: EntryContainer = fileSystem.readToml(newPath, toml)
		assertEquals(newEntry, writtenEntry.entry)
	}

	@Test
	fun `Update Entry Name - Invalid`() = runTest {
		val oldEntry = entry1()
		val origDef = oldEntry.toDef(projDef)

		val repo = createRepository()

		/////////////////////
		// Too long
		var newName = "A : Invalid"
		var newEntry = oldEntry.copy(
			name = newName
		)
		var result = repo.updateEntry(
			oldEntryDef = origDef,
			name = newName,
			text = oldEntry.text,
			tags = oldEntry.tags
		)

		assertInvalid(EntryError.NAME_INVALID_CHARACTERS, result, repo, oldEntry, newEntry)

		/////////////////////
		// Too short
		newName = ""
		newEntry = oldEntry.copy(
			name = newName
		)
		result = repo.updateEntry(
			oldEntryDef = origDef,
			name = newName,
			text = oldEntry.text,
			tags = oldEntry.tags
		)

		assertInvalid(EntryError.NAME_TOO_SHORT, result, repo, oldEntry, newEntry)

		/////////////////////
		// Too long
		newName = "x".repeat(MAX_NAME_SIZE + 1)
		newEntry = oldEntry.copy(
			name = newName
		)
		result = repo.updateEntry(
			oldEntryDef = origDef,
			name = newName,
			text = oldEntry.text,
			tags = oldEntry.tags
		)

		assertInvalid(EntryError.NAME_TOO_LONG, result, repo, oldEntry, newEntry)
	}

	@Test
	fun `Create Entry`() = runTest {
		coEvery { idRepository.claimNextId() } returns 3

		val container = EntryContainer(
			entry = EntryContent(
				id = 3,
				name = "Entry Name",
				type = EntryType.PERSON,
				text = "Entry content",
				tags = setOf("tag1", "tag2")
			)
		)

		val repo = createRepository()
		val result = repo.createEntry(
			name = container.entry.name,
			type = container.entry.type,
			text = container.entry.text,
			tags = container.entry.tags,
			imagePath = null,
			forceId = null,
		)

		assertEquals(EntryError.NONE, result.error)
		assertEquals(container, result.instance)

		val path = datasource.getEntryPath(container.entry).toOkioPath()
		assertTrue(fileSystem.exists(path))
		val loadedEntry: EntryContainer = fileSystem.readToml(path, toml)
		assertEquals(container, loadedEntry)
	}

	@Test
	fun `Delete Entry`() = runTest {
		val deletionIdSlot = slot<Int>()
		coEvery { syncDataRepository.recordIdDeletion(capture(deletionIdSlot)) } just Runs

		val repo = createRepository()
		val deleted = repo.deleteEntry(entry1().toDef(projDef))
		assertTrue(deleted)

		val path = datasource.getEntryPath(entry1().toDef(projDef)).toOkioPath()
		assertFalse(fileSystem.exists(path))
		assertEquals(entry1().id, deletionIdSlot.captured)
		coVerify { syncDataRepository.recordIdDeletion(any()) }
	}

	@Test
	fun `Set Entry Image`() = runTest {
		val repo = createRepository()
		//repo.setEntryImage(entry1().toDef(projDef), "image.png")
		// TODO implement image tests at some point
	}

	@Test
	fun `Remove Entry Image`() = runTest {
		val repo = createRepository()
		// TODO implement image tests at some point
	}

	@Test
	fun `Load Entry Image`() = runTest {
		val repo = createRepository()
		// TODO implement image tests at some point
	}

	@Test
	fun `ReId Entry`() = runTest {
		val repo = createRepository()

		val path = datasource.getEntryPath(entry2().toDef(projDef)).toOkioPath()
		assertTrue(fileSystem.exists(path))

		repo.reIdEntry(2, 3)

		assertFalse(fileSystem.exists(path))

		val newDef = entry2().copy(id = 3).toDef(projDef)
		val newPath = datasource.getEntryPath(newDef).toOkioPath()
		assertTrue(fileSystem.exists(newPath))
	}

	@Test
	fun `Load Entry`() = runTest {
		val repo = createRepository()
		val container = repo.loadEntry(entry1().toDef(projDef))

		assertEquals(entry1(), container.entry)
	}

	@Test
	fun `Find Entry`() = runTest {
		val repo = createRepository()
		val entryDef = repo.findEntryDef(entry1().id)
		assertEquals(entry1().toDef(projDef), entryDef)
	}

	@Test
	fun `Find Entry that doesn't exist`() = runTest {
		val repo = createRepository()
		val entryDef = repo.findEntryDef(7)
		assertNull(entryDef)
	}

	private fun assertInvalid(
		expectedError: EntryError,
		result: EntryResult,
		repo: EncyclopediaRepository,
		oldEntry: EntryContent,
		newEntry: EntryContent
	) {
		assertEquals(expectedError, result.error)

		val newPath = datasource.getEntryPath(newEntry).toOkioPath()
		assertFalse(fileSystem.exists(newPath))

		val oldPath = datasource.getEntryPath(oldEntry).toOkioPath()
		val writtenEntry: EntryContainer = fileSystem.readToml(oldPath, toml)
		assertEquals(oldEntry, writtenEntry.entry)
	}
}