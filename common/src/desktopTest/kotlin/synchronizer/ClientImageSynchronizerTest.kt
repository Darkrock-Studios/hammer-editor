package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceRewriter
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientImageSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import korlibs.crypto.encoding.Base64
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
import utils.TestStrRes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientImageSynchronizerTest : BaseTest() {

	private val projectDef = ProjectDef(
		id = testProjectId("Test"),
		name = "Test",
		path = "/projects/Test".toPath().toHPath(),
	)

	private lateinit var ffs: FakeFileSystem
	private lateinit var syncJournal: SyncJournal
	private lateinit var datasource: ImagesDatasource
	private lateinit var repository: ImagesRepository
	private lateinit var rewriter: ImageReferenceRewriter

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		ffs.createDirectories(projectDef.path.toOkioPath())
		syncJournal = mockk(relaxed = true)
		datasource = ImagesDatasource(projectDef, ffs)
		rewriter = mockk()
		coEvery { rewriter.remap(any()) } returns emptySet()

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				scoped { repository }
				scoped { rewriter }
			}
		})
		repository = ImagesRepository(projectDef, mockk<IdAllocator>(relaxed = true), syncJournal, datasource)
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	private fun newSynchronizer() = ClientImageSynchronizer(
		projectDef = projectDef,
		serverProjectApi = mockk<ServerProjectApi>(),
		strRes = TestStrRes(),
	)

	private fun entity(
		id: Int = 5,
		name: String = "Castle Map",
		fileExtension: String = "png",
		bytes: ByteArray = TestImages.png(),
	) = ApiProjectEntity.ImageEntity(
		id = id,
		name = name,
		fileExtension = fileExtension,
		base64 = Base64.encode(bytes, url = true),
	)

	private fun imageFiles(): List<String> =
		ffs.listOrNull(projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY).orEmpty().map { it.name }

	@Test
	fun `getEntityType is Image`() {
		assertEquals(EntityType.Image, newSynchronizer().getEntityType())
	}

	@Test
	fun `Local hash equals the API hash after a store, for every format and name shape`() = runTest {
		val sync = newSynchronizer()
		val cases = listOf(
			entity(id = 1, fileExtension = "png", bytes = TestImages.png(1)),
			entity(id = 2, name = "map.v2 final", fileExtension = "jpg", bytes = TestImages.jpeg(2)),
			entity(id = 3, name = "Ünïcödé: what? / why*", fileExtension = "gif", bytes = TestImages.gif(3)),
			entity(id = 4, name = "Vector", fileExtension = "svg", bytes = TestImages.svg()),
			entity(id = 6, name = " leading space", fileExtension = "jpeg", bytes = TestImages.jpeg(6)),
		)

		for (server in cases) {
			assertTrue(sync.storeEntity(server, "sync", {}), server.name)

			val local = sync.createEntityForId(server.id)
			assertEquals(server, local, "Stored image must read back unchanged: ${server.name}")
			assertEquals(local.hash(), sync.getEntityHash(server.id), "Hash parity for ${server.name}")
			assertEquals(server.hash(), sync.getEntityHash(server.id), "Matches the server for ${server.name}")
		}
	}

	@Test
	fun `hashEntities covers every image except new ids`() = runTest {
		val sync = newSynchronizer()
		sync.storeEntity(entity(id = 1, bytes = TestImages.png(1)), "sync", {})
		sync.storeEntity(entity(id = 2, bytes = TestImages.png(2)), "sync", {})

		val hashes = sync.hashEntities(newIds = listOf(2))

		assertEquals(setOf(1), hashes.map { it.id }.toSet())
		assertEquals(sync.getEntityHash(1), hashes.single().hash)
	}

	@Test
	fun `Replacing with a new extension leaves one file and the new hash`() = runTest {
		val sync = newSynchronizer()
		sync.storeEntity(entity(fileExtension = "png", bytes = TestImages.png()), "sync", {})
		val before = sync.getEntityHash(5)

		val replacement = entity(name = "Castle Map", fileExtension = "jpg", bytes = TestImages.jpeg())
		assertTrue(sync.storeEntity(replacement, "sync", {}))

		assertEquals(listOf("5~Castle Map.jpg"), imageFiles())
		assertNotEquals(before, sync.getEntityHash(5))
		assertEquals(replacement.hash(), sync.getEntityHash(5))
	}

	@Test
	fun `Replacing with the same size bytes still changes the hash`() = runTest {
		val sync = newSynchronizer()
		sync.storeEntity(entity(bytes = TestImages.png(1)), "sync", {})
		val first = sync.getEntityHash(5)

		sync.storeEntity(entity(bytes = TestImages.png(2)), "sync", {})

		assertNotEquals(first, sync.getEntityHash(5))
		assertEquals(entity(bytes = TestImages.png(2)).hash(), sync.getEntityHash(5))
	}

	@Test
	fun `A server rename moves the file`() = runTest {
		val sync = newSynchronizer()
		sync.storeEntity(entity(name = "Old"), "sync", {})

		sync.storeEntity(entity(name = "New"), "sync", {})

		assertEquals(listOf("5~New.png"), imageFiles())
	}

	@Test
	fun `storeEntity does not mark the image dirty`() = runTest {
		newSynchronizer().storeEntity(entity(), "sync", {})

		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
		coVerify(exactly = 0) { syncJournal.recordIdDeletion(any()) }
	}

	@Test
	fun `storeEntity rejects invalid images with a warning and writes nothing`() = runTest {
		val sync = newSynchronizer()
		val rejected = listOf(
			entity(fileExtension = "exe"),
			entity(fileExtension = "png", bytes = "not a png".encodeToByteArray()),
			entity(name = "bad~name"),
			entity(name = "trailing."),
			entity(name = "Map\uA789North"),
			entity(name = ""),
			entity().copy(base64 = "%%% not base64 %%%"),
			entity(bytes = TestImages.png() + ByteArray(ImageFormat.MAX_IMAGE_BYTES.toInt())),
		)

		for (server in rejected) {
			val logs = mutableListOf<SyncLogMessage>()
			assertFalse(sync.storeEntity(server, "sync") { logs += it }, server.name)
			assertEquals(SyncLogLevel.WARN, logs.single().level)
		}
		assertTrue(imageFiles().isEmpty())
	}

	@Test
	fun `A name with path characters is stored encoded inside the images directory`() = runTest {
		val sync = newSynchronizer()

		assertTrue(sync.storeEntity(entity(name = "../escape"), "sync", {}))

		assertEquals(listOf("5~..\u2044escape.png"), imageFiles())
		assertEquals("../escape", sync.createEntityForId(5).name)
	}

	@Test
	fun `ownsEntity and getEntityHash read the list prepareForSync loaded`() = runTest {
		val dir = projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY
		ffs.createDirectories(dir)
		ffs.write(dir / "5~Castle Map.png") { write(TestImages.png()) }
		val sync = newSynchronizer()

		sync.prepareForSync()

		assertTrue(sync.ownsEntity(5))
		assertEquals(entity().hash(), sync.getEntityHash(5))
		assertFalse(sync.ownsEntity(6))
		assertNull(sync.getEntityHash(6))
	}

	@Test
	fun `deleteEntityLocal removes the file only and logs`() = runTest {
		val sync = newSynchronizer()
		sync.storeEntity(entity(), "sync", {})
		val logs = mutableListOf<SyncLogMessage>()

		sync.deleteEntityLocal(5) { logs += it }

		assertTrue(imageFiles().isEmpty())
		assertFalse(sync.ownsEntity(5))
		assertNull(sync.getEntityHash(5))
		assertEquals(SyncLogLevel.INFO, logs.single().level)
		coVerify(exactly = 0) { syncJournal.recordIdDeletion(any()) }
	}

	@Test
	fun `reIdEntity renames the file, then rewrites references and reports the referrers`() = runTest {
		val sync = newSynchronizer()
		sync.storeEntity(entity(id = 5), "sync", {})
		coEvery { rewriter.remap(mapOf(5 to 9)) } answers {
			assertEquals(listOf("9~Castle Map.png"), imageFiles(), "The file must move before references do")
			setOf(TouchedEntity(EntityType.EncyclopediaEntry, 3), TouchedEntity(EntityType.EncyclopediaEntry, 4))
		}

		val referrers = sync.reIdEntity(oldId = 5, newId = 9)

		assertEquals(setOf(3, 4), referrers)
		assertEquals(listOf("9~Castle Map.png"), imageFiles())
		assertFalse(sync.ownsEntity(5))
		assertTrue(sync.ownsEntity(9))
		assertEquals(entity(id = 9).hash(), sync.getEntityHash(9))
	}

	@Test
	fun `createEntityForId sends the bytes on disk`() = runTest {
		val sync = newSynchronizer()
		sync.storeEntity(entity(bytes = TestImages.gif(7), fileExtension = "gif"), "sync", {})

		val local = sync.createEntityForId(5)

		assertContentEquals(TestImages.gif(7), Base64.decode(local.base64, url = true))
	}
}
