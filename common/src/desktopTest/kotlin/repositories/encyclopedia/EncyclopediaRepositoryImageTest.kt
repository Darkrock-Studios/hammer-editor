package repositories.encyclopedia

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import utils.TestImages
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An entry's ordered list of Image entity ids, and creating those images from picked files. */
class EncyclopediaRepositoryImageTest : BaseTest() {

	private val projDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	private lateinit var idAllocator: IdAllocator
	private lateinit var externalFileIo: ExternalFileIo
	private lateinit var syncJournal: SyncJournal
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: EncyclopediaDatasource
	private lateinit var imagesRepository: ImagesRepository

	private var nextId = 100
	private val sourcePath = "/external/portrait.png"

	@BeforeEach
	override fun setup() {
		super.setup()
		idAllocator = mockk()
		externalFileIo = mockk()
		syncJournal = mockk(relaxUnitFun = true)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projDef }
			}
		})
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false
		coEvery { idAllocator.claimNextId() } answers { nextId++ }
		every { externalFileIo.readExternalFile(sourcePath) } returns TestImages.png()

		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		createProject(fileSystem, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
	}

	private fun repository(entryFileSystem: FileSystem = fileSystem): EncyclopediaRepository {
		datasource = EncyclopediaDatasource(projectDef = projDef, toml = toml, fileSystem = entryFileSystem)
		return EncyclopediaRepository(
			projectDef = projDef,
			idAllocator = idAllocator,
			datasource = datasource,
			syncJournal = syncJournal,
		)
	}

	private fun service(repository: EncyclopediaRepository = repository()): EncyclopediaService {
		imagesRepository = ImagesRepository(projDef, idAllocator, syncJournal, ImagesDatasource(projDef, fileSystem))
		return EncyclopediaService(
			repository = repository,
			statisticsRepository = mockk(relaxed = true),
			referenceIndexRepository = mockk(relaxed = true),
			imagesRepository = imagesRepository,
			externalFileIo = externalFileIo,
		)
	}

	private fun EncyclopediaRepository.images(id: Int = 1): List<Int> = loadEntry(id).entry.images

	@Test
	fun `addEntryImage appends in order, ignores a repeat, and marks the entry for sync`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)

		assertTrue(repo.addEntryImage(def, 7))
		assertTrue(repo.addEntryImage(def, 3))
		assertFalse(repo.addEntryImage(def, 7))

		assertEquals(listOf(7, 3), repo.images())
		coVerify { syncJournal.markEntityAsDirty(def.id) }
	}

	@Test
	fun `removeEntryImage detaches only that image`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)
		listOf(7, 3, 9).forEach { repo.addEntryImage(def, it) }

		assertTrue(repo.removeEntryImage(def, 3))
		assertFalse(repo.removeEntryImage(def, 42))

		assertEquals(listOf(7, 9), repo.images())
	}

	@Test
	fun `setPrimaryImage moves the image to the front`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)
		listOf(7, 3, 9).forEach { repo.addEntryImage(def, it) }

		assertTrue(repo.setPrimaryImage(def, 9))
		assertFalse(repo.setPrimaryImage(def, 9))
		assertFalse(repo.setPrimaryImage(def, 42))

		assertEquals(listOf(9, 7, 3), repo.images())
	}

	@Test
	fun `addPrimaryImage puts the image first, adding it when missing`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)
		listOf(7, 3).forEach { repo.addEntryImage(def, it) }

		assertTrue(repo.addPrimaryImage(def, 9))
		assertEquals(listOf(9, 7, 3), repo.images())
		assertTrue(repo.addPrimaryImage(def, 3))
		assertEquals(listOf(3, 9, 7), repo.images())
		assertFalse(repo.addPrimaryImage(def, 3))
	}

	@Test
	fun `an unchanged list writes nothing and marks nothing`() = runTest {
		val repo = repository()
		val def = entry1().toDef(projDef)

		assertFalse(repo.removeEntryImage(def, 5))

		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}

	@Test
	fun `rewriteEntry writes an entry that would fail validation`() = runTest {
		val repo = repository()
		val tooLongTag = "t".repeat(EncyclopediaRepository.MAX_TAG_SIZE + 1)
		datasource.createEntry(EntryContainer(entry1().copy(tags = setOf(tooLongTag))))
		val def = entry1().toDef(projDef)
		assertEquals(EntryError.TAG_TOO_LONG, repo.validateEntry(def.name, def.type, "", setOf(tooLongTag)))

		assertTrue(repo.rewriteEntry(def) { it.copy(text = "rewritten", images = listOf(4)) })

		val stored = repo.loadEntry(1).entry
		assertEquals("rewritten", stored.text)
		assertEquals(listOf(4), stored.images)
		assertEquals(setOf(tooLongTag), stored.tags)
		coVerify { syncJournal.markEntityAsDirty(1) }
	}

	@Test
	fun `createEntry with a picked image creates an Image named after the entry and lists it`() = runTest {
		val service = service()

		val result = service.createEntry(
			name = "Alice",
			type = EntryType.PERSON,
			text = "",
			tags = emptySet(),
			imagePath = sourcePath,
		)

		assertEquals(EntryError.NONE, result.error)
		val entry = assertNotNull(result.instance).entry
		val image = assertNotNull(imagesRepository.findImage(entry.images.single()))
		assertEquals("Alice", image.name)
		assertEquals("png", image.fileExtension)
		assertEquals(image.id, assertNotNull(service.findPrimaryImage(entry.images)).id)
		// The entry is written first, so it claims its id before the image does.
		assertTrue(entry.id < image.id)
	}

	@Test
	fun `createEntry puts a picked image ahead of the listed ones`() = runTest {
		val repo = repository()
		val service = service(repo)

		val result = service.createEntry(
			name = "Alice",
			type = EntryType.PERSON,
			text = "",
			tags = emptySet(),
			imagePath = sourcePath,
			images = listOf(7),
		)

		val entry = assertNotNull(result.instance).entry
		val image = assertNotNull(imagesRepository.getImages().single())
		assertEquals(listOf(image.id, 7), entry.images)
		assertEquals(listOf(image.id, 7), repo.loadEntry(entry.id).entry.images)
	}

	@Test
	fun `createEntry whose entry cannot be written creates no Image`() = runTest {
		val failingEntryWrites = object : ForwardingFileSystem(fileSystem) {
			override fun sink(file: Path, mustCreate: Boolean): Sink {
				if (file.name.endsWith(".toml")) throw IOException("disk full")
				return super.sink(file, mustCreate)
			}
		}
		val service = service(repository(failingEntryWrites))

		assertFailsWith<IOException> {
			service.createEntry(
				name = "Alice",
				type = EntryType.PERSON,
				text = "",
				tags = emptySet(),
				imagePath = sourcePath,
			)
		}
		assertTrue(imagesRepository.loadImages().isEmpty())
	}

	@Test
	fun `createEntry with an invalid name creates no Image`() = runTest {
		val service = service()

		val result = service.createEntry(
			name = "",
			type = EntryType.PERSON,
			text = "",
			tags = emptySet(),
			imagePath = sourcePath,
		)

		assertEquals(EntryError.NAME_TOO_SHORT, result.error)
		assertTrue(imagesRepository.loadImages().isEmpty())
	}

	@Test
	fun `createEntry with an image that is not valid creates the entry without one`() = runTest {
		val heic = "/external/photo.heic"
		val tooBig = "/external/huge.png"
		val unreadable = "/external/gone.png"
		every { externalFileIo.readExternalFile(heic) } returns TestImages.png()
		every { externalFileIo.readExternalFile(tooBig) } returns ByteArray(9 * 1024 * 1024)
		every { externalFileIo.readExternalFile(unreadable) } throws IOException("gone")
		val service = service()

		listOf(heic, tooBig, unreadable).forEachIndexed { i, path ->
			val result = service.createEntry(
				name = "Entry $i",
				type = EntryType.PERSON,
				text = "",
				tags = emptySet(),
				imagePath = path,
			)
			assertEquals(EntryError.NONE, result.error)
			assertTrue(assertNotNull(result.instance).entry.images.isEmpty())
		}
		assertTrue(imagesRepository.loadImages().isEmpty())
	}

	@Test
	fun `importEntryImage adds the new image after the others`() = runTest {
		val repo = repository()
		val service = service(repo)
		val def = entry1().toDef(projDef)
		repo.addEntryImage(def, 7)

		val outcome = service.importEntryImage(def, sourcePath)

		assertEquals(ImageAddResult.Ok, outcome.result)
		val image = assertNotNull(outcome.image)
		assertEquals("Entry 1", image.name)
		assertEquals(listOf(7, image.id), repo.images())
	}

	@Test
	fun `importEntryImage into an entry without images makes it the primary image`() = runTest {
		val repo = repository()
		val service = service(repo)
		val def = entry1().toDef(projDef)

		val outcome = service.importEntryImage(def, "png", TestImages.png())

		val image = assertNotNull(outcome.image)
		assertEquals(listOf(image.id), repo.images())
		assertEquals(image.id, service.findPrimaryImage(repo.images())?.id)
	}

	@Test
	fun `importEntryImage that is not a valid image leaves the entry alone`() = runTest {
		val repo = repository()
		val service = service(repo)
		val def = entry1().toDef(projDef)

		val outcome = service.importEntryImage(def, "jpg", TestImages.png())

		assertEquals(ImageAddResult.UnsupportedType, outcome.result)
		assertTrue(repo.images().isEmpty())
		assertTrue(imagesRepository.loadImages().isEmpty())
	}

	@Test
	fun `removeEntryImage through the service leaves the Image in the project`() = runTest {
		val repo = repository()
		val service = service(repo)
		val def = entry1().toDef(projDef)
		val image = assertNotNull(service.importEntryImage(def, sourcePath).image)

		assertTrue(service.removeEntryImage(def, image.id))

		assertTrue(repo.images().isEmpty())
		assertEquals(image, imagesRepository.findImage(image.id))
	}

	@Test
	fun `entryImageFiles lists the images in the project in entry order`() = runTest {
		val repo = repository()
		val service = service(repo)
		val def = entry1().toDef(projDef)
		val map = assertNotNull(imagesRepository.create("Map", TestImages.png(), "png").image)
		val sketch = assertNotNull(imagesRepository.create("Sketch", TestImages.png(), "png").image)
		repo.addEntryImage(def, sketch.id)
		repo.addEntryImage(def, 55)
		repo.addEntryImage(def, map.id)

		val files = service.entryImageFiles(repo.loadEntry(def).entry)

		assertEquals(listOf(sketch, map), files.map { it.def })
		assertEquals(imagesRepository.getImagePath(sketch), files.first().path)
		// Keyed by the file stamp, as nothing has hashed the image yet: listing reads no bytes.
		assertEquals(imagesRepository.getFileStamp(sketch)?.cacheToken, files.first().contentHash)
	}

	@Test
	fun `findPrimaryImage skips ids with no image in the project`() = runTest {
		val repo = repository()
		val service = service(repo)
		val def = entry1().toDef(projDef)
		val created = assertNotNull(imagesRepository.create("Map", TestImages.png(), "png").image)
		repo.addEntryImage(def, 55)
		repo.addEntryImage(def, created.id)

		assertEquals(created.id, service.findPrimaryImage(repo.images())?.id)
		assertNull(service.findPrimaryImage(listOf(55)))
	}

	@Test
	fun `an entry file without images reads as an empty list`() = runTest {
		val repo = repository()
		assertTrue(repo.loadEntry(2).entry.images.isEmpty())
	}
}
