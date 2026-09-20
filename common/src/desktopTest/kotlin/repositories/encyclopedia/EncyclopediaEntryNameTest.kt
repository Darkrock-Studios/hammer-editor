package repositories.encyclopedia

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
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
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the widened entry-name character set (data v3) end to end: what the repository accepts,
 * what lands on disk, and that a name survives the round trip through the filename.
 */
class EncyclopediaEntryNameTest : BaseTest() {

	private val projDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	@MockK
	lateinit var idAllocator: IdAllocator

	@MockK
	lateinit var externalFileIo: ExternalFileIo

	@MockK
	lateinit var syncJournal: SyncJournal

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var datasource: EncyclopediaDatasource
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

	private fun createRepository(): EncyclopediaRepository {
		datasource = EncyclopediaDatasource(
			projectDef = projDef,
			toml = toml,
			fileSystem = fileSystem,
			externalFileIo = externalFileIo,
		)
		return EncyclopediaRepository(
			projectDef = projDef,
			idAllocator = idAllocator,
			datasource = datasource,
			syncJournal = syncJournal,
		)
	}

	private suspend fun create(repo: EncyclopediaRepository, name: String, id: Int = 50) =
		repo.createEntry(
			name = name,
			type = EntryType.PERSON,
			text = "text",
			tags = emptySet(),
			imagePath = null,
			forceId = id,
		)

	@Test
	fun `names with punctuation that used to be rejected are now accepted`() = runTest {
		val repo = createRepository()
		val names = listOf(
			"Mr. Finch",
			"Ael'thara, the Drowned",
			"Who Goes There?",
			"Bag End (Hobbiton)",
			"Smith & Sons",
			"Chapter 3: The Fall",
			"Half-Elf",
			"Stop!",
			"The “Masked” Man",
		)

		names.forEachIndexed { index, name ->
			val result = create(repo, name, id = 100 + index)
			assertEquals(EntryError.NONE, result.error, "'$name' should be a legal entry name")
		}
	}

	@Test
	fun `the reserved filename delimiter is still rejected`() = runTest {
		val repo = createRepository()

		val result = create(repo, "Bad~Name")

		assertEquals(EntryError.NAME_INVALID_CHARACTERS, result.error)
	}

	@Test
	fun `a trailing dot is rejected because the on-disk encoder would strip it`() = runTest {
		val repo = createRepository()

		val result = create(repo, "Mr. Finch.")

		assertEquals(EntryError.NAME_INVALID_CHARACTERS, result.error)
	}

	@Test
	fun `a name with OS-forbidden characters is stored under a safe filename and round-trips`() =
		runTest {
			val repo = createRepository()
			val name = "Chapter 3: The Fall?"

			assertEquals(EntryError.NONE, create(repo, name).error)

			val def = repo.getEntryDef(50)
			assertEquals(name, def.name, "The display name must survive the filename encoding")

			val path = datasource.getEntryPath(def).toOkioPath()
			assertTrue(fileSystem.exists(path))
			assertFalse(path.name.contains(':'), "Filename must not contain a forbidden char")
			assertFalse(path.name.contains('?'), "Filename must not contain a forbidden char")
			assertEquals("text", repo.loadEntry(50).entry.text)
		}

	@Test
	fun `an entry stored under the legacy filename is still found, read and deleted`() = runTest {
		val repo = createRepository()
		val typeDir = EncyclopediaDatasource
			.getTypeDirectory(projDef, EntryType.PERSON, fileSystem)
			.toOkioPath()
		val legacyPath = typeDir / "person-77-Old Timer.toml"
		fileSystem.write(legacyPath) {
			writeUtf8(
				"""
				[entry]
				id = 77
				name = "Old Timer"
				type = "PERSON"
				text = "legacy"
				tags = []
				""".trimIndent()
			)
		}

		val def = repo.getEntryDef(77)
		assertEquals("Old Timer", def.name)
		assertEquals("legacy", repo.loadEntry(77).entry.text)

		repo.deleteEntry(def)
		assertFalse(fileSystem.exists(legacyPath), "The legacy file must actually be deleted")
	}

	@Test
	fun `renaming a legacy entry rewrites it under the current filename format`() = runTest {
		val repo = createRepository()
		val typeDir = EncyclopediaDatasource
			.getTypeDirectory(projDef, EntryType.PERSON, fileSystem)
			.toOkioPath()
		val legacyPath = typeDir / "person-78-Old Timer.toml"
		fileSystem.write(legacyPath) {
			writeUtf8(
				"""
				[entry]
				id = 78
				name = "Old Timer"
				type = "PERSON"
				text = "legacy"
				tags = []
				""".trimIndent()
			)
		}

		val result = repo.updateEntry(
			oldEntryDef = repo.getEntryDef(78),
			name = "Old Timer, Esq",
			text = "legacy",
			tags = emptySet(),
			excludeFromDictionary = false,
		)

		assertEquals(EntryError.NONE, result.error)
		assertFalse(fileSystem.exists(legacyPath))
		assertTrue(fileSystem.exists(typeDir / "person~78~Old Timer, Esq.toml"))
	}
}
