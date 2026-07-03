package repositories.encyclopedia

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncyclopediaRepositoryImageTest : BaseTest() {

	private val projDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	private lateinit var idAllocator: IdAllocator
	private lateinit var externalFileIo: ExternalFileIo
	private lateinit var syncJournal: SyncJournal
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: EncyclopediaDatasource

	private val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
	private val sourcePath = "/external/portrait.jpg"

	@BeforeEach
	override fun setup() {
		super.setup()
		idAllocator = mockk()
		externalFileIo = mockk()
		syncJournal = mockk(relaxUnitFun = true)
		setupKoin()
		every { syncJournal.isServerSynchronized() } returns false
		every { externalFileIo.readExternalFile(sourcePath) } returns imageBytes

		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		createProject(fileSystem, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
	}

	private fun repository(): EncyclopediaRepository {
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

	@Test
	fun `setEntryImage stores the image and marks the entry for sync`() = runTest {
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false

		val repo = repository()
		val def = entry1().toDef(projDef)

		repo.setEntryImage(def, sourcePath)

		assertTrue(repo.hasEntryImage(def, "jpg"))
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(def.id) }
	}

	@Test
	fun `setEntryImage preserves a supported source extension`() = runTest {
		val pngSource = "/external/art.png"
		every { externalFileIo.readExternalFile(pngSource) } returns imageBytes

		val repo = repository()
		val def = entry1().toDef(projDef)

		repo.setEntryImage(def, pngSource)

		assertTrue(repo.hasEntryImage(def, "png"))
		assertFalse(repo.hasEntryImage(def, "jpg"))
		assertTrue(datasource.findEntryImagePath(def)!!.name.endsWith(".png"))
	}

	@Test
	fun `setEntryImage ignores an unsupported source extension`() = runTest {
		val heicSource = "/external/photo.heic"
		every { externalFileIo.readExternalFile(heicSource) } returns imageBytes

		val repo = repository()
		val def = entry1().toDef(projDef)

		repo.setEntryImage(def, heicSource)

		assertNull(datasource.findEntryImagePath(def))
	}

	@Test
	fun `setEntryImage with an unsupported extension preserves an existing image`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)
		repo.setEntryImage(def, sourcePath)

		val heicSource = "/external/photo.heic"
		every { externalFileIo.readExternalFile(heicSource) } returns imageBytes
		repo.setEntryImage(def, heicSource)

		assertTrue(datasource.findEntryImagePath(def)!!.name.endsWith(".jpg"))
	}

	@Test
	fun `setEntryImage ignores an image over the size limit`() = runTest {
		val bigSource = "/external/huge.png"
		val tooBig = ByteArray((EncyclopediaDatasource.MAX_IMAGE_SIZE_BYTES + 1).toInt())
		every { externalFileIo.readExternalFile(bigSource) } returns tooBig

		val repo = repository()
		val def = entry1().toDef(projDef)

		repo.setEntryImage(def, bigSource)

		assertNull(datasource.findEntryImagePath(def))
	}

	@Test
	fun `setEntryImage over the size limit preserves an existing image`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)
		repo.setEntryImage(def, sourcePath)

		val bigSource = "/external/huge.png"
		val tooBig = ByteArray((EncyclopediaDatasource.MAX_IMAGE_SIZE_BYTES + 1).toInt())
		every { externalFileIo.readExternalFile(bigSource) } returns tooBig
		repo.setEntryImage(def, bigSource)

		assertTrue(datasource.findEntryImagePath(def)!!.name.endsWith(".jpg"))
	}

	@Test
	fun `setEntryImage with a null path removes the existing image`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)
		repo.setEntryImage(def, sourcePath)

		repo.setEntryImage(def, null)

		assertFalse(repo.hasEntryImage(def, "jpg"))
	}

	@Test
	fun `loadEntryImage and getEntryImagePath round-trip the stored bytes`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)
		repo.setEntryImage(def, sourcePath)

		assertContentEquals(imageBytes, repo.loadEntryImage(def, "jpg"))
		assertTrue(repo.getEntryImagePath(def, "jpg").name.endsWith("jpg"))
	}

	@Test
	fun `calculateEntryImageHash returns a hash only when an image exists`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)

		assertNull(repo.calculateEntryImageHash(def, "jpg"))

		repo.setEntryImage(def, sourcePath)
		assertNotNull(repo.calculateEntryImageHash(def, "jpg"))
	}

	@Test
	fun `calculateEntryImageHash changes when the image is replaced with different bytes`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)

		repo.setEntryImage(def, sourcePath)
		val originalHash = repo.calculateEntryImageHash(def, "jpg")

		val replacementSource = "/external/replacement.jpg"
		every { externalFileIo.readExternalFile(replacementSource) } returns byteArrayOf(9, 8, 7, 6)
		repo.setEntryImage(def, replacementSource)
		val replacedHash = repo.calculateEntryImageHash(def, "jpg")

		assertNotNull(originalHash)
		assertNotNull(replacedHash)
		assertNotEquals(originalHash, replacedHash)
	}

	@Test
	fun `removeEntryImage deletes an existing image and marks for sync`() = runTest {
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false

		val repo = repository()
		val def = entry1().toDef(projDef)
		repo.setEntryImage(def, sourcePath)

		val removed = repo.removeEntryImage(def)

		assertTrue(removed)
		assertFalse(repo.hasEntryImage(def, "jpg"))
		coVerify { syncJournal.markEntityAsDirty(def.id) }
	}

	@Test
	fun `removeEntryImage returns false and skips sync when deletion fails`() = runTest {
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false

		val repo = repository()
		val def = entry1().toDef(projDef)

		// A non-empty directory at the image path makes the file delete throw.
		val imagePath = datasource.getEntryImagePath(def, "jpg").toOkioPath()
		fileSystem.createDirectories(imagePath)
		fileSystem.write(imagePath / "child") { writeUtf8("x") }

		val removed = repo.removeEntryImage(def)

		assertFalse(removed)
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}

	@Test
	fun `ensureEntriesLoaded loads entries from disk and serves them on the second call`() = runTest {
		val repo = repository()
		datasource.createEntry(EntryContainer(entry1()))
		datasource.createEntry(EntryContainer(entry2()))

		val first = repo.ensureEntriesLoaded()
		val second = repo.ensureEntriesLoaded()

		assertTrue(first.map { it.id }.containsAll(listOf(1, 2)))
		assertContentEquals(first, second)
	}
}
