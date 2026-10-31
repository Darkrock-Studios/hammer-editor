package repositories.images

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.datasources.ImageIdDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.findImageFile
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testProjectId
import utils.BaseTest
import utils.TestImages
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagesDatasourceTest : BaseTest() {

	private val projectDef = ProjectDef(
		id = testProjectId("Test"),
		name = "Test",
		path = "/projects/Test".toPath().toHPath(),
	)
	private val imagesDir get() = projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY

	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		ffs.createDirectories(projectDef.path.toOkioPath())
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	private fun writeRaw(name: String, bytes: ByteArray = TestImages.png()) {
		ffs.createDirectories(imagesDir)
		ffs.write(imagesDir / name) { write(bytes) }
	}

	@Test
	fun `Filenames round trip, dots and encoded characters included`() {
		val defs = listOf(
			ImageDef(1, "Map", "png"),
			ImageDef(22, "map.v2.final", "jpeg"),
			ImageDef(3, "What? Why: / \\ * | < > \"", "svg"),
		)
		for (def in defs) {
			assertEquals(def, ImagesDatasource.parseFilename(ImagesDatasource.getFilename(def)))
		}
	}

	@Test
	fun `Files that are not images are not parsed`() {
		assertNull(ImagesDatasource.parseFilename("1~Map.txt"))
		assertNull(ImagesDatasource.parseFilename("Map.png"))
		assertNull(ImagesDatasource.parseFilename("x~Map.png"))
		assertNull(ImagesDatasource.parseFilename(".1~Map.png"))
		assertNull(ImagesDatasource.parseFilename("1~Map"))
		assertNull(ImagesDatasource.parseFilename("1~a~b.png"))
	}

	@Test
	fun `writeImage replaces any other file holding the id`() {
		val datasource = ImagesDatasource(projectDef, ffs)
		writeRaw("4~Map.png")

		datasource.writeImage(ImageDef(4, "Map", "webp"), TestImages.webp())

		assertEquals(listOf("4~Map.webp"), ffs.list(imagesDir).map { it.name })
		assertContentEquals(TestImages.webp(), datasource.loadImageBytes(ImageDef(4, "Map", "webp")))
	}

	@Test
	fun `writeImage over the same name replaces the bytes`() {
		val datasource = ImagesDatasource(projectDef, ffs)
		writeRaw("4~Map.png", TestImages.png(1))

		datasource.writeImage(ImageDef(4, "Map", "png"), TestImages.png(2))

		assertEquals(listOf("4~Map.png"), ffs.list(imagesDir).map { it.name })
		assertContentEquals(TestImages.png(2), datasource.loadImageBytes(ImageDef(4, "Map", "png")))
	}

	@Test
	fun `writeImage replaces a file whose name differs only in case`() {
		val datasource = ImagesDatasource(projectDef, ffs)
		writeRaw("4~map.png", TestImages.png(1))

		datasource.writeImage(ImageDef(4, "Map", "png"), TestImages.png(2))

		assertEquals(listOf("4~Map.png"), ffs.list(imagesDir).map { it.name })
	}

	@Test
	fun `A failed move keeps the old image and removes the temp file`() {
		val failingMoves = object : ForwardingFileSystem(ffs) {
			override fun atomicMove(source: Path, target: Path) {
				throw IOException("disk full")
			}
		}
		val datasource = ImagesDatasource(projectDef, failingMoves)
		writeRaw("4~Map.png", TestImages.png(1))

		assertFailsWith<IOException> { datasource.writeImage(ImageDef(4, "Map", "webp"), TestImages.webp()) }

		assertEquals(listOf("4~Map.png"), ffs.list(imagesDir).map { it.name })
		assertContentEquals(TestImages.png(1), ffs.read(imagesDir / "4~Map.png") { readByteArray() })
	}

	@Test
	fun `reIdImage and deleteImage act on the file holding the id`() {
		val datasource = ImagesDatasource(projectDef, ffs)
		writeRaw("4~Map.png")

		assertEquals(ImageDef(9, "Map", "png"), datasource.reIdImage(4, 9))
		assertNull(datasource.findImage(4))
		assertTrue(datasource.deleteImage(9))
		assertFalse(datasource.deleteImage(9))
		assertTrue(ffs.list(imagesDir).isEmpty())
	}

	@Test
	fun `findImageFile works without a scope and creates nothing`() {
		assertNull(findImageFile(projectDef, 4, ffs))
		assertFalse(ffs.exists(imagesDir))

		writeRaw("4~Cover.webp", "RIFF\u0000\u0000\u0000\u0000WEBP".encodeToByteArray())
		assertEquals(imagesDir / "4~Cover.webp", findImageFile(projectDef, 4, ffs)?.toOkioPath())
	}

	@Test
	fun `ImageIdDatasource reads ids from filenames`() {
		val ids = ImageIdDatasource(ffs)
		assertEquals(emptySet(), ids.findIds(projectDef))

		writeRaw("4~Map.png")
		writeRaw("11~Other.jpg")
		writeRaw("readme.txt")

		assertEquals(setOf(4, 11), ids.findIds(projectDef))
		assertEquals(11, ids.findHighestId(projectDef))
	}

	@Test
	fun `validImageName keeps a valid name and cleans the rest`() {
		assertEquals("Map of the North", ImagesDatasource.validImageName("  Map of the North "))
		assertEquals("Who: me?", ImagesDatasource.validImageName("Who: me?"))
		assertEquals("Chapter 1", ImagesDatasource.validImageName("Chapter 1."))
		assertEquals("Chapter 1", ImagesDatasource.validImageName("Chapter 1 ."))
		assertEquals("Bob the Builder", ImagesDatasource.validImageName("Bob~the\tBuilder"))
		assertEquals("a".repeat(127), ImagesDatasource.validImageName("a".repeat(127) + " b" + "~"))
		assertEquals(ImagesDatasource.FALLBACK_IMAGE_NAME, ImagesDatasource.validImageName("~~~"))
		assertEquals(ImagesDatasource.FALLBACK_IMAGE_NAME, ImagesDatasource.validImageName(" . "))
	}
}
