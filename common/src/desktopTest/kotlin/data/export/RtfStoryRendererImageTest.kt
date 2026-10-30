package data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.buildStoryRtf
import com.darkrockstudios.apps.hammer.common.data.export.builtInStoryExporters
import com.darkrockstudios.apps.hammer.common.data.export.decodedOnce
import com.darkrockstudios.apps.hammer.common.data.export.exportRaster
import com.darkrockstudios.apps.hammer.common.data.export.writeStoryAsRtf
import com.darkrockstudios.libs.rtfparserkmp.model.PictureFormat
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfAlignment
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfBlock
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfDocumentWriter
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfPageBreak
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfPicture
import com.darkrockstudios.libs.rtfparserkmp.parser.RtfEvent
import com.darkrockstudios.libs.rtfparserkmp.parser.parseRtf
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RtfStoryRendererImageTest {

	private class Pict(
		val format: String,
		val widthPx: Int,
		val heightPx: Int,
		val goalWidth: Int,
		val goalHeight: Int,
		val bytes: ByteArray,
		/** The paragraph properties between `\pard` and the picture group. */
		val paragraph: String,
	)

	private fun render(image: ExportImage, markdown: String = "Before.\n\n![image](hammer://image/3)\n\nAfter."): String {
		val buffer = Buffer()
		writeStoryAsRtf(
			sink = buffer,
			projectName = "Story",
			projectData = ProjectData(authorName = "Author"),
			chapters = listOf(StoryChapter("Alpha", markdown)),
			strings = ExportStrings(contentsTitle = "Contents", authorByline = "by Author"),
			imageNames = mapOf(3 to "Cheshire Cat"),
			images = mapOf(3 to image),
		)
		return buffer.readByteArray().decodeToString()
	}

	private fun picts(rtf: String): List<Pict> = PICT.findAll(rtf).map { match ->
		val (paragraph, format, w, h, gw, gh, hex) = match.destructured
		Pict(
			format = format,
			widthPx = w.toInt(),
			heightPx = h.toInt(),
			goalWidth = gw.toInt(),
			goalHeight = gh.toInt(),
			bytes = hex.filterNot { it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
			paragraph = paragraph,
		)
	}.toList()

	private fun pict(extension: String, bytes: ByteArray): Pict =
		picts(render(ExportImage(3, "Cheshire Cat", extension, bytes))).single()

	@Test
	fun `a png on its own line is embedded as it is at 70 percent of the text width`() {
		val png = encoded("png")
		val pict = pict("png", png)

		assertEquals("pngblip", pict.format)
		assertEquals(40 to 20, pict.widthPx to pict.heightPx)
		assertEquals(6048 to 3024, pict.goalWidth to pict.goalHeight)
		assertContentEquals(png, pict.bytes)
	}

	@Test
	fun `a jpeg on its own line is embedded as it is`() {
		val jpeg = encoded("jpg")
		val pict = pict("jpg", jpeg)

		assertEquals("jpegblip", pict.format)
		assertEquals(40 to 20, pict.widthPx to pict.heightPx)
		assertEquals(6048 to 3024, pict.goalWidth to pict.goalHeight)
		assertContentEquals(jpeg, pict.bytes)
	}

	@Test
	fun `an rtf reader reads the picture back`() {
		val png = encoded("png")
		val rtf = render(ExportImage(3, "Cheshire Cat", "png", png))

		val picture = parseRtf(rtf.encodeToByteArray()).filterIsInstance<RtfEvent.Picture>().single()
		assertEquals(PictureFormat.Png, picture.format)
		assertEquals(40 to 20, picture.width to picture.height)
		assertEquals(6048 to 3024, picture.goalWidthTwips to picture.goalHeightTwips)
		assertContentEquals(png, picture.bytes)
	}

	@Test
	fun `a small image scales up to the same width`() {
		val pict = pict("png", encoded("png", width = 4, height = 3))

		assertEquals(6048 to 4536, pict.goalWidth to pict.goalHeight)
	}

	@Test
	fun `a tall image is capped at 60 percent of the text height and keeps its shape`() {
		val pict = pict("png", encoded("png", width = 20, height = 40))

		assertEquals(3888 to 7776, pict.goalWidth to pict.goalHeight)
	}

	@Test
	fun `a webp is embedded as png`() {
		val pict = pict("webp", webp())

		assertEquals("pngblip", pict.format)
		assertEquals(40 to 20, pict.widthPx to pict.heightPx)
		assertEquals(6048 to 3024, pict.goalWidth to pict.goalHeight)
	}

	@Test
	fun `a turned jpeg is embedded upright as png`() {
		val turned = withExifOrientation(encoded("jpg", width = 40, height = 20), orientation = 6)
		val pict = pict("jpg", turned)

		assertEquals("pngblip", pict.format)
		assertEquals(20 to 40, pict.widthPx to pict.heightPx)
		assertEquals(3888 to 7776, pict.goalWidth to pict.goalHeight)
	}

	@Test
	fun `the picture is centred a paragraph gap from prose but not from a typed blank line`() {
		val image = ExportImage(3, "Cheshire Cat", "png", encoded("png"))

		val nextToProse = picts(render(image, "Before.\n![image](hammer://image/3)\nAfter.")).single()
		assertEquals("\\qc\\sb160\\sa160", nextToProse.paragraph)

		// Each typed blank line is a paragraph of its own, which already parts the picture from prose.
		val parted = picts(render(image, "Before.\n\n![image](hammer://image/3)\n\nAfter.")).single()
		assertEquals("\\qc", parted.paragraph)
	}

	@Test
	fun `an image used twice is embedded twice`() {
		val image = ExportImage(3, "Cheshire Cat", "webp", webp())
		val rtf = render(image, "![image](hammer://image/3)\n\nMiddle.\n\n![image](hammer://image/3)")

		assertEquals(2, picts(rtf).size, rtf)
		assertTrue(!rtf.contains("Cheshire Cat"), rtf)
	}

	@Test
	fun `each image is decoded once however often it is shown, even when it fails`() {
		val decoded = mutableListOf<Int>()
		val images = mapOf(
			3 to ExportImage(3, "Cheshire Cat", "png", encoded("png")),
			4 to ExportImage(4, "Broken", "webp", "not an image".encodeToByteArray()),
		)
		val picture = decodedOnce(images) { image ->
			decoded += image.id
			exportRaster(image)
		}

		repeat(3) {
			assertNotNull(picture(3))
			assertNull(picture(4))
			assertNull(picture(5))
		}
		assertEquals(listOf(3, 4), decoded)
	}

	@Test
	fun `the rtf exporter is handed the project images`() {
		val exporter = builtInStoryExporters.single { it.formatId == BuiltInExportFormat.RTF }
		assertTrue(exporter.embedsImages)

		val buffer = Buffer()
		exporter.render(
			buffer,
			ExportInput(
				projectName = "Story",
				projectData = ProjectData(authorName = "Author"),
				chapters = listOf(StoryChapter("Alpha", "![image](hammer://image/3)")),
				treatTopLevelAsChapters = true,
				options = ExportOptions(),
				language = "en",
				strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
				images = mapOf(3 to ExportImage(3, "Cheshire Cat", "png", encoded("png"))),
				imageNames = mapOf(3 to "Cheshire Cat"),
			),
		)
		assertNotNull(picts(buffer.readByteArray().decodeToString()).singleOrNull())
	}

	/** The blocks of a one-chapter story whose project cover is image 3. */
	private fun coverBlocks(images: Map<Int, ExportImage>, coverImageId: Int? = 3): List<RtfBlock> = buildStoryRtf(
		projectName = "Story",
		projectData = ProjectData(authorName = "Author", coverImageId = coverImageId),
		chapters = listOf(StoryChapter("Alpha", "Body.")),
		strings = ExportStrings(contentsTitle = "Contents", authorByline = "by Author"),
		images = images,
	).blocks

	@Test
	fun `a cover fills the text width of a page of its own before the title page`() {
		val png = encoded("png", width = 40, height = 20)
		val blocks = coverBlocks(mapOf(3 to ExportImage(3, "Cover", "png", png)))

		val cover = assertIs<RtfPicture>(blocks[0])
		assertEquals(8640 to 4320, cover.goalWidthTwips to cover.goalHeightTwips)
		assertEquals(RtfAlignment.Center, cover.style.alignment)
		assertContentEquals(png, cover.bytes)
		assertEquals(RtfPageBreak, blocks[1])
		assertEquals(coverBlocks(emptyMap(), coverImageId = null), blocks.drop(2))
	}

	@Test
	fun `a tall cover is held to the text height`() {
		val blocks = coverBlocks(mapOf(3 to ExportImage(3, "Cover", "png", encoded("png", width = 20, height = 80))))

		val cover = assertIs<RtfPicture>(blocks[0])
		assertEquals(3240 to 12960, cover.goalWidthTwips to cover.goalHeightTwips)
	}

	@Test
	fun `the cover is written as a picture group before the title`() {
		val rtf = RtfDocumentWriter().write(
			buildStoryRtf(
				projectName = "Story",
				projectData = ProjectData(coverImageId = 3),
				chapters = listOf(StoryChapter("Alpha", "Body.")),
				strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
				images = mapOf(3 to ExportImage(3, "Cover", "png", encoded("png"))),
			),
		)

		val pict = picts(rtf).single()
		assertEquals(8640 to 4320, pict.goalWidth to pict.goalHeight)
		assertEquals("\\qc", pict.paragraph)
		assertTrue(rtf.indexOf("\\pict") < rtf.indexOf("\\page"), rtf)
	}

	@Test
	fun `a cover that is missing or not a raster leaves the document as it is without one`() {
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><rect width="20" height="10"/></svg>"""
		val without = coverBlocks(emptyMap(), coverImageId = null)

		for (images in listOf(
			emptyMap(),
			mapOf(3 to ExportImage(3, "Cover", "svg", svg.encodeToByteArray())),
			mapOf(3 to ExportImage(3, "Cover", "png", "not an image".encodeToByteArray())),
		)) {
			assertEquals(without, coverBlocks(images), images.toString())
		}
	}

	private companion object {
		val PICT = Regex(
			"""\\pard((?:\\[a-z]+\d*)*) \{\\pict\\(pngblip|jpegblip)\\picw(\d+)\\pich(\d+)\\picwgoal(\d+)\\pichgoal(\d+) ([0-9a-f\s]+)}""",
		)
	}
}
