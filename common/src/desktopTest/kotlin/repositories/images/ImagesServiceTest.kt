package repositories.images

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceRewriter
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferrer
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
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
import okio.IOException
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.encyclopedia.entry1
import repositories.encyclopedia.entry2
import utils.BaseTest
import utils.TestImages
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagesServiceTest : BaseTest() {

	private val projectDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	private lateinit var ffs: FakeFileSystem
	private lateinit var syncJournal: SyncJournal
	private lateinit var idAllocator: IdAllocator
	private lateinit var externalFileIo: ExternalFileIo
	private lateinit var encyclopediaDatasource: EncyclopediaDatasource
	private lateinit var encyclopedia: EncyclopediaRepository
	private lateinit var imagesDatasource: ImagesDatasource
	private lateinit var images: ImagesRepository
	private lateinit var projectDataRepository: ProjectDataRepository
	private var projectData = ProjectData()

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createProject(ffs, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
		syncJournal = mockk(relaxed = true)
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false
		idAllocator = mockk()
		coEvery { idAllocator.claimNextId() } returnsMany listOf(40, 41, 42)
		externalFileIo = mockk()
		projectDataRepository = mockk()
		coEvery { projectDataRepository.load() } answers { StoredProjectData(projectData) }
		coEvery { projectDataRepository.updateData(any()) } answers {
			projectData = firstArg<(ProjectData) -> ProjectData>()(projectData)
		}

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				scoped { encyclopedia }
				scoped { projectDataRepository }
			}
		})
		encyclopediaDatasource = EncyclopediaDatasource(projectDef, createTomlSerializer(), ffs)
		encyclopedia = EncyclopediaRepository(projectDef, idAllocator, encyclopediaDatasource, syncJournal)
		imagesDatasource = ImagesDatasource(projectDef, ffs)
		images = ImagesRepository(projectDef, idAllocator, syncJournal, imagesDatasource)
	}

	private fun service() = ImagesService(images, ImageReferenceRewriter(projectDef), externalFileIo)

	private suspend fun store(entry: EntryContent) {
		encyclopediaDatasource.createEntry(EntryContainer(entry))
	}

	private fun entry(id: Int): EntryContent = encyclopedia.loadEntry(id).entry

	private fun writeImage(def: ImageDef, bytes: ByteArray = TestImages.png()) {
		imagesDatasource.writeImage(def, bytes)
	}

	@Test
	fun `delete takes the image out of entry text and image lists, marks them dirty, then deletes it`() = runTest {
		val map = ImageDef(5, "Map", "png")
		writeImage(map)
		writeImage(ImageDef(6, "Coast", "png"))
		store(entry1().copy(text = "Top\n\n![Map](hammer://image/5)\n\nBottom", images = listOf(6, 5)))
		store(entry2().copy(images = listOf(5)))
		val untouched = entry1().copy(id = 3, name = "Entry 3", images = listOf(6))
		store(untouched)

		val result = service().delete(5)

		assertTrue(result.deleted)
		assertEquals(
			setOf(TouchedEntity(EntityType.EncyclopediaEntry, 1), TouchedEntity(EntityType.EncyclopediaEntry, 2)),
			result.touched,
		)
		assertEquals("Top\n\nBottom", entry(1).text)
		assertEquals(listOf(6), entry(1).images)
		assertTrue(entry(2).images.isEmpty())
		assertEquals(listOf(6), entry(3).images)
		coVerify { syncJournal.markEntityAsDirty(1) }
		coVerify { syncJournal.markEntityAsDirty(2) }
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(3) }
		coVerify { syncJournal.recordIdDeletion(5) }
		assertNull(images.findImage(5))
		assertFalse(ffs.exists(imagesDatasource.getImagePath(map).toOkioPath()))
	}

	@Test
	fun `deleting the cover image clears the cover and reports it`() = runTest {
		writeImage(ImageDef(5, "Cover", "png"))
		projectData = ProjectData(coverImageId = 5)

		val result = service().delete(5)

		assertTrue(result.deleted)
		assertTrue(result.coverCleared)
		assertTrue(result.touched.isEmpty())
		assertEquals(listOf<ImageReferrer>(ImageReferrer.ProjectCover), result.removedFrom)
		assertNull(projectData.coverImageId)
	}

	@Test
	fun `deleting an image that is not there rewrites nothing`() = runTest {
		store(entry1().copy(images = listOf(5)))

		val result = service().delete(5)

		assertFalse(result.deleted)
		assertTrue(result.touched.isEmpty())
		assertEquals(listOf(5), entry(1).images)
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}

	@Test
	fun `a picked file is added under its own name`() = runTest {
		every { externalFileIo.readExternalFile("/picked/Old Harbour.JPG") } returns TestImages.jpeg()

		val outcome = service().addFromPath("/picked/Old Harbour.JPG")

		assertEquals(ImageAddResult.Ok, outcome.result)
		assertEquals(ImageDef(40, "Old Harbour", "jpg"), outcome.image)
		coVerify { syncJournal.markEntityAsDirty(40) }
	}

	@Test
	fun `a file that cannot be read is reported`() = runTest {
		every { externalFileIo.readExternalFile(any()) } throws IOException("gone")

		assertEquals(ImageAddResult.ReadFailed, service().addFromPath("/picked/gone.png").result)
		assertEquals(ImageAddResult.ReadFailed, service().replaceFromPath(5, "/picked/gone.png").result)
	}

	@Test
	fun `replacing keeps the id and name and takes the new extension`() = runTest {
		writeImage(ImageDef(5, "Map", "png"))
		val webp = TestImages.webp()
		every { externalFileIo.readExternalFile("/picked/new.webp") } returns webp

		val outcome = service().replaceFromPath(5, "/picked/new.webp")

		assertEquals(ImageDef(5, "Map", "webp"), outcome.image)
		assertContentEquals(webp, images.loadImageBytes(ImageDef(5, "Map", "webp")))
		assertEquals(listOf(ImageDef(5, "Map", "webp")), images.loadImages())
	}

	@Test
	fun `used image ids come from entry lists and text`() = runTest {
		store(entry1().copy(text = "![a](hammer://image/7)", images = listOf(5)))
		store(entry2().copy(images = listOf(5, 6)))

		assertEquals(setOf(5, 6, 7), service().findUsedImageIds())
	}
}
