package repositories.images

import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import testProjectId
import utils.BaseTest
import utils.TestImages
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ImagesRepositoryTest : BaseTest() {

	private val projectDef = ProjectDef(
		id = testProjectId("Test"),
		name = "Test",
		path = "/projects/Test".toPath().toHPath(),
	)

	private lateinit var ffs: FakeFileSystem
	private lateinit var idAllocator: IdAllocator
	private lateinit var syncJournal: SyncJournal
	private lateinit var datasource: ImagesDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		ffs.createDirectories(projectDef.path.toOkioPath())
		idAllocator = mockk()
		syncJournal = mockk(relaxed = true)
		datasource = ImagesDatasource(projectDef, ffs)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
			}
		})
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	private fun createRepository() = ImagesRepository(projectDef, idAllocator, syncJournal, datasource)

	private fun imageFiles(): List<String> =
		ffs.listOrNull(projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY).orEmpty().map { it.name }

	private fun writeRaw(name: String, bytes: ByteArray) {
		val dir = projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY
		ffs.createDirectories(dir)
		ffs.write(dir / name) { write(bytes) }
	}

	@Test
	fun `create claims an id, writes the file and marks it dirty`() = runTest {
		coEvery { idAllocator.claimNextId() } returns 12
		val repo = createRepository()

		val outcome = repo.create("  Castle Map ", TestImages.png(), "PNG")

		assertEquals(ImageAddResult.Ok, outcome.result)
		assertEquals(ImageDef(12, "Castle Map", "png"), outcome.image)
		assertEquals(listOf("12~Castle Map.png"), imageFiles())
		assertEquals(listOf(ImageDef(12, "Castle Map", "png")), repo.getImages())
		coVerify { syncJournal.markEntityAsDirty(12) }
	}

	@Test
	fun `create validates before claiming an id`() = runTest {
		val repo = createRepository()

		assertEquals(ImageAddResult.UnsupportedType, repo.create("Map", TestImages.png(), "bmp").result)
		assertEquals(ImageAddResult.UnsupportedType, repo.create("Map", "text".encodeToByteArray(), "png").result)
		assertEquals(ImageAddResult.NameInvalid, repo.create("bad~name", TestImages.png(), "png").result)
		assertEquals(ImageAddResult.NameInvalid, repo.create("   ", TestImages.png(), "png").result)
		val tooBig = TestImages.png() + ByteArray(ImageFormat.MAX_IMAGE_BYTES.toInt())
		assertEquals(ImageAddResult.TooLarge, repo.create("Map", tooBig, "png").result)

		coVerify(exactly = 0) { idAllocator.claimNextId() }
		assertTrue(imageFiles().isEmpty())
	}

	@Test
	fun `replace swaps the bytes and extension and marks dirty`() = runTest {
		writeRaw("3~Map.png", TestImages.png())
		val repo = createRepository()

		val outcome = repo.replace(3, TestImages.jpeg(), "jpg")

		assertEquals(ImageAddResult.Ok, outcome.result)
		assertEquals(listOf("3~Map.jpg"), imageFiles())
		coVerify { syncJournal.markEntityAsDirty(3) }
	}

	@Test
	fun `rename moves the file and marks dirty`() = runTest {
		writeRaw("3~Map.png", TestImages.png())
		val repo = createRepository()

		assertEquals(ImageAddResult.NameInvalid, repo.rename(3, "bad~name").result)
		assertEquals(ImageAddResult.Ok, repo.rename(3, "Old Map").result)

		assertEquals(listOf("3~Old Map.png"), imageFiles())
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(3) }
	}

	@Test
	fun `delete removes the file and records the deletion`() = runTest {
		writeRaw("3~Map.png", TestImages.png())
		val repo = createRepository()

		assertTrue(repo.delete(3))

		assertTrue(imageFiles().isEmpty())
		coVerify { syncJournal.recordIdDeletion(3) }
	}

	@Test
	fun `contentHash is the image hash of the file and follows edits`() = runTest {
		writeRaw("3~Map.png", TestImages.png(1))
		val repo = createRepository()

		val map = ImageDef(3, "Map", "png")
		val first = repo.contentHash(map)
		assertEquals(EntityHasher.hashImage(3, "Map", "png", TestImages.png(1)), first)
		assertEquals(first, repo.contentHash(map), "Cached value is stable")

		// Written behind the repository's back: the changed size invalidates the cached hash.
		val edited = TestImages.png(1) + byteArrayOf(9)
		writeRaw("3~Map.png", edited)
		assertEquals(EntityHasher.hashImage(3, "Map", "png", edited), repo.contentHash(map))

		// A rename changes the hashed name, so it must not be served from the cache.
		val atlas = assertNotNull(repo.rename(3, "Atlas").image)
		assertEquals(EntityHasher.hashImage(3, "Atlas", "png", edited), repo.contentHash(atlas))

		assertNull(repo.contentHash(ImageDef(99, "Gone", "png")))
	}

	@Test
	fun `cacheVersion is the cached hash, else the file stamp, and never reads the file`() = runTest {
		writeRaw("3~Map.png", TestImages.png(1))
		val spied = spyk(datasource)
		val repo = ImagesRepository(projectDef, idAllocator, syncJournal, spied)
		val def = ImageDef(3, "Map", "png")
		val stamp = repo.getFileStamp(def)!!

		assertEquals(stamp.cacheToken, repo.cacheVersion(def))
		verify(exactly = 0) { spied.loadImageBytes(any()) }

		val hash = repo.contentHash(def)
		assertEquals(hash, repo.cacheVersion(def))
		assertEquals(hash, repo.imageFile(def).contentHash)
		assertNull(repo.cacheVersion(ImageDef(99, "Gone", "png")))
	}

	@Test
	fun `cachedContentHash only answers from the cache and never reads the file`() = runTest {
		writeRaw("3~Map.png", TestImages.png(1))
		val spied = spyk(datasource)
		val repo = ImagesRepository(projectDef, idAllocator, syncJournal, spied)
		val def = ImageDef(3, "Map", "png")
		val stamp = repo.getFileStamp(def)!!

		assertNull(repo.cachedContentHash(def, stamp))
		verify(exactly = 0) { spied.loadImageBytes(any()) }

		val hash = repo.contentHash(def)
		assertEquals(hash, repo.cachedContentHash(def, stamp))
		assertNull(repo.cachedContentHash(def, stamp.copy(size = stamp.size + 1)))
		assertNull(repo.cachedContentHash(def.copy(name = "Other"), stamp))
	}

	@Test
	fun `imagesFlow emits the loaded images`() = runTest {
		writeRaw("2~B.png", TestImages.png())
		writeRaw("1~A.gif", TestImages.gif())
		writeRaw("notes.txt", "x".encodeToByteArray())
		val repo = createRepository()

		repo.imagesFlow.test(timeout = 5.seconds) {
			assertEquals(listOf(ImageDef(1, "A", "gif"), ImageDef(2, "B", "png")), awaitItem())
		}
	}
}
