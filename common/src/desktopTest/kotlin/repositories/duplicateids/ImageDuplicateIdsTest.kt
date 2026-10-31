package repositories.duplicateids

import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import utils.TestImages
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageDuplicateIdsTest : RationalizerTestBase() {

	private fun writeImage(relative: String, bytes: ByteArray) {
		val path = projectDir / relative
		ffs.createDirectories(path.parent!!)
		ffs.write(path) { write(bytes) }
	}

	@Test
	fun `Two images with one id are a duplicate set`() = runTest {
		writeImage("images/5~Map.png", TestImages.png(1))
		writeImage("images/5~Map.jpg", TestImages.jpeg(1))

		val set = onlySet(DuplicateIdKind.Image)

		assertEquals(5, set.id)
		assertEquals(listOf("images/5~Map.jpg", "images/5~Map.png"), set.copies.map { it.relativePath })
	}

	@Test
	fun `Keeping both gives the extra one a fresh id`() = runTest {
		writeImage("images/5~Map.png", TestImages.png(1))
		writeImage("images/5~Atlas.png", TestImages.png(2))
		val set = onlySet(DuplicateIdKind.Image)

		rationalizer.resolve(
			set,
			keep = setOf(set.pathOf("images/5~Atlas.png"), set.pathOf("images/5~Map.png")),
			claimId = ids(6),
		)

		assertEquals(listOf("5~Atlas.png", "6~Map.png"), filesIn("images"))
		assertTrue(rationalizer.findDuplicates().isEmpty())
	}

	@Test
	fun `Copies with different names are left for the user`() = runTest {
		writeImage("images/5~Map.png", TestImages.png(1))
		writeImage("images/5~Map copy.png", TestImages.png(1))

		val resolution = rationalizer.resolveSafeDuplicates()

		assertEquals(1, resolution.remaining.size)
		assertEquals(2, filesIn("images").size)
	}
}
