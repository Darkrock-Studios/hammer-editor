package repositories.encyclopedia

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository.Companion.MAX_NAME_SIZE
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncyclopediaRepositoryTest : BaseTest() {

	val projDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	@MockK
	lateinit var idRepository: IdRepository

	@MockK
	lateinit var externalFileIo: ExternalFileIo

	@MockK
	lateinit var projectSynchronizer: ClientProjectSynchronizer

	lateinit var fileSystem: FakeFileSystem
	lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		val testModule = module {
			//single { encyclopediaRepository } bind EncyclopediaRepository::class
		}
		setupKoin(testModule)

		every { projectSynchronizer.isServerSynchronized() } returns false
		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		createProject(fileSystem, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
	}

	@Test
	fun `Update Entry Name - Valid`() = runTest {
		val entry = entry1()
		val origDef = entry.toDef(projDef)

		val newValidName = "A new name"
		val newEntry = entry.copy(
			name = newValidName
		)

		val repo = EncyclopediaRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepository,
			toml = toml,
			fileSystem = fileSystem,
			externalFileIo = externalFileIo,
			projectSynchronizer = projectSynchronizer,
		)

		val result = repo.updateEntry(
			oldEntryDef = origDef,
			name = newValidName,
			text = entry.text,
			tags = entry.tags
		)


		assertEquals(EntryError.NONE, result.error)
		assertEquals(newEntry, result.instance?.entry)

		val newPath = repo.getEntryPath(newEntry).toOkioPath()
		assertTrue(fileSystem.exists(newPath))

		val writtenEntry: EntryContainer = fileSystem.readToml(newPath, toml)
		assertEquals(newEntry, writtenEntry.entry)
	}

	@Test
	fun `Update Entry Name - Invalid`() = runTest {
		val oldEntry = entry1()
		val origDef = oldEntry.toDef(projDef)

		val repo = EncyclopediaRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepository,
			toml = toml,
			fileSystem = fileSystem,
			externalFileIo = externalFileIo,
			projectSynchronizer = projectSynchronizer,
		)

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

	private fun assertInvalid(
		expectedError: EntryError,
		result: EntryResult,
		repo: EncyclopediaRepositoryOkio,
		oldEntry: EntryContent,
		newEntry: EntryContent
	) {
		assertEquals(expectedError, result.error)

		val newPath = repo.getEntryPath(newEntry).toOkioPath()
		assertFalse(fileSystem.exists(newPath))

		val oldPath = repo.getEntryPath(oldEntry).toOkioPath()
		val writtenEntry: EntryContainer = fileSystem.readToml(oldPath, toml)
		assertEquals(oldEntry, writtenEntry.entry)
	}
}