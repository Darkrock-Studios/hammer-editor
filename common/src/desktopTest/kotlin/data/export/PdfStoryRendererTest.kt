package data.export

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.defaultPdfDriverFactory
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.image.readImageInfo
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.render.PdfCanvas
import com.conamobile.pdfkmp.render.PdfDriver
import com.conamobile.pdfkmp.render.PdfDriverFactory
import com.conamobile.pdfkmp.style.PdfFont
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.ProseBlock
import com.darkrockstudios.apps.hammer.common.data.export.ProseColors
import com.darkrockstudios.apps.hammer.common.data.export.ProseLayout
import com.darkrockstudios.apps.hammer.common.data.export.ProsePicture
import com.darkrockstudios.apps.hammer.common.data.export.STORY_PAGE_PADDING
import com.darkrockstudios.apps.hammer.common.data.export.STORY_PAGE_SIZE
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.buildStoryPdf
import com.darkrockstudios.apps.hammer.common.data.export.prepareProse
import com.darkrockstudios.apps.hammer.common.data.export.prosePicture
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PdfStoryRendererTest {

	private val drawnImages = mutableListOf<DrawnImage>()

	private fun render(
		markdown: String,
		imageNames: Map<Int, String> = emptyMap(),
		images: Map<Int, ExportImage> = emptyMap(),
	): PdfDocument = buildStoryPdf(
		projectName = "Story",
		projectData = ProjectData(authorName = "Author"),
		chapters = listOf(StoryChapter("Chapter", markdown)),
		strings = ExportStrings(contentsTitle = "Contents", authorByline = "by Author"),
		imageNames = imageNames,
		images = images,
		factory = ImageWatchingFactory(drawnImages),
	)

	/** An opaque image, so the PDF holds one image object for it and no soft mask. */
	private fun encoded(format: String, width: Int = 40, height: Int = 20): ByteArray {
		val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
		image.createGraphics().apply {
			color = java.awt.Color.ORANGE
			fillRect(0, 0, width, height)
			dispose()
		}
		return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
	}

	private fun webp(width: Int = 40, height: Int = 20): ByteArray {
		val surface = Surface.makeRasterN32Premul(width, height)
		surface.canvas.clear(Color.makeRGB(255, 160, 0))
		return checkNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.WEBP)).bytes
	}

	private fun imageObjects(document: PdfDocument): Int =
		IMAGE_SUBTYPE.findAll(document.toByteArray().toString(Charsets.ISO_8859_1)).count()

	private fun renderImage(bytes: ByteArray, extension: String): PdfDocument = render(
		"Before.\n\n![image](hammer://image/3)\n\nAfter.",
		imageNames = mapOf(3 to "Cheshire"),
		images = mapOf(3 to ExportImage(id = 3, name = "Cheshire", fileExtension = extension, bytes = bytes)),
	)

	private fun assertInsideMargins(document: PdfDocument) {
		val top = STORY_PAGE_PADDING.top.value
		val bottom = STORY_PAGE_SIZE.height.value - STORY_PAGE_PADDING.bottom.value
		val right = STORY_PAGE_SIZE.width.value - STORY_PAGE_PADDING.right.value
		for (run in document.textRuns) {
			assertTrue(run.yPoints >= top - 0.5f, "run above the top margin: $run")
			assertTrue(run.yPoints + run.heightPoints <= bottom + 0.5f, "run below the bottom margin: $run")
			assertTrue(run.xPoints + run.widthPoints <= right + 0.5f, "run past the right margin: $run")
		}
	}

	private fun sentences(count: Int): String =
		(1..count).joinToString(" ") { "Sentence number $it has some **bold** and *italic* words in it." }

	@Test
	fun `a paragraph taller than a page flows onto the next page`() {
		val document = render(sentences(400) + " THE-END")

		assertInsideMargins(document)
		val chapterPages = document.textRuns.map { it.pageIndex }.distinct().count { it >= 2 }
		assertTrue(chapterPages >= 3, "expected the paragraph to span pages, got $chapterPages")
		assertTrue(document.textRuns.last().text.contains("THE-END"))
	}

	@Test
	fun `a paragraph that crosses a page boundary fills the page before breaking`() {
		val filler = (1..30).joinToString("\n") { "Short line $it." }
		val document = render(filler + "\n" + sentences(60))

		assertInsideMargins(document)
		val firstChapterPage = document.textRuns.filter { it.pageIndex == 2 }
		val bottom = STORY_PAGE_SIZE.height.value - STORY_PAGE_PADDING.bottom.value
		val lowest = firstChapterPage.maxOf { it.yPoints + it.heightPoints }
		assertTrue(bottom - lowest < 40f, "page left ${bottom - lowest}pt empty before the break")
	}

	private fun assertSpansPages(document: PdfDocument) {
		assertInsideMargins(document)
		val chapterPages = document.textRuns.map { it.pageIndex }.distinct().count { it >= 2 }
		assertTrue(chapterPages >= 2, "expected the block to span pages, got $chapterPages")
		assertTrue(document.textRuns.any { it.text.contains("THE-END") })
	}

	@Test
	fun `a quote taller than a page flows onto the next page`() {
		assertSpansPages(render("> " + sentences(300) + " THE-END"))
	}

	@Test
	fun `a list item taller than a page flows onto the next page`() {
		assertSpansPages(render("- short item\n- " + sentences(300) + " THE-END"))
	}

	@Test
	fun `a code block taller than a page flows onto the next page`() {
		val code = (1..120).joinToString("\n") { "line $it" }
		assertSpansPages(render("```\n$code\nTHE-END\n```"))
	}

	@Test
	fun `a table row taller than a page continues in further rows`() {
		assertSpansPages(render("| a | b |\n|---|---|\n| short | " + sentences(200) + " THE-END |"))
	}

	@Test
	fun `every line of a right-to-left paragraph keeps the paragraph direction`() {
		val words = (1..240).joinToString(" ") { if (it % 3 == 0) "Alpha" else "שלום" }
		val document = render(words)

		val right = STORY_PAGE_SIZE.width.value - STORY_PAGE_PADDING.right.value
		val rows = document.textRuns.filter { it.pageIndex == 2 }.drop(1).groupBy { it.yPoints }
		assertTrue(rows.size > 5)
		for ((y, runs) in rows) {
			val edge = runs.maxOf { it.xPoints + it.widthPoints }
			assertTrue(right - edge < 8f, "line at y=$y ends ${right - edge}pt short of the right margin")
		}
	}

	@Test
	fun `pre-wrapped lines are not wrapped again`() {
		val document = render(sentences(40))

		val rows = document.textRuns.filter { it.pageIndex == 2 }.map { it.yPoints }.distinct().sorted()
		val gaps = rows.drop(1).zipWithNext { a, b -> b - a }.map { Math.round(it * 10) }.distinct()
		assertEquals(1, gaps.size, "uneven line spacing: $gaps")
	}

	@Test
	fun `a project image is written as its current name`() {
		val document = render("![image](hammer://image/3)", imageNames = mapOf(3 to "Cheshire"))

		assertTrue(document.textRuns.any { it.text.contains("Cheshire") }, document.textRuns.toString())
		assertTrue(document.textRuns.none { it.text.contains("image") }, document.textRuns.toString())
		assertEquals(0, imageObjects(document))
	}

	@Test
	fun `a png is drawn in place of its name`() {
		val document = renderImage(encoded("png"), "png")

		assertEquals(1, imageObjects(document))
		assertTrue(document.textRuns.none { it.text.contains("Cheshire") }, document.textRuns.toString())
		assertTrue(document.textRuns.any { it.text.contains("After.") })
	}

	@Test
	fun `a jpeg is drawn in place of its name`() {
		val document = renderImage(encoded("jpg"), "jpg")

		assertEquals(1, imageObjects(document))
		assertTrue(document.textRuns.none { it.text.contains("Cheshire") }, document.textRuns.toString())
	}

	@Test
	fun `a webp is drawn through its png form`() {
		val document = renderImage(webp(), "webp")

		assertEquals(1, imageObjects(document))
		assertTrue(document.textRuns.none { it.text.contains("Cheshire") }, document.textRuns.toString())
	}

	@Test
	fun `an svg is drawn as a vector in place of its name`() {
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><rect width="20" height="10" fill="#f80"/></svg>"""
		val document = renderImage(svg.encodeToByteArray(), "svg")

		assertTrue(document.textRuns.none { it.text.contains("Cheshire") }, document.textRuns.toString())
	}

	@Test
	fun `an image whose header cannot be read is written as its name`() {
		for ((bytes, extension) in listOf(
			"not an image".encodeToByteArray() to "png",
			"not an image".encodeToByteArray() to "webp",
			"<svg".encodeToByteArray() to "svg",
		)) {
			val document = renderImage(bytes, extension)

			assertEquals(0, imageObjects(document), extension)
			assertTrue(document.textRuns.any { it.text.contains("Cheshire") }, "$extension: ${document.textRuns}")
		}
	}

	@Test
	fun `an image with an extension Hammer does not allow is written as its name`() {
		val document = renderImage(encoded("bmp"), "bmp")

		assertEquals(0, imageObjects(document))
		assertTrue(document.textRuns.any { it.text.contains("Cheshire") }, document.textRuns.toString())
	}

	@Test
	fun `an image inside a line of text is written as its name`() {
		val document = render(
			"See ![image](hammer://image/3) here.",
			imageNames = mapOf(3 to "Cheshire"),
			images = mapOf(3 to ExportImage(id = 3, name = "Cheshire", fileExtension = "png", bytes = encoded("png"))),
		)

		assertEquals(0, imageObjects(document))
		assertTrue(document.textRuns.any { it.text.contains("Cheshire") }, document.textRuns.toString())
	}

	@Test
	fun `an image too tall for the rest of a page moves to the next`() {
		val filler = (1..30).joinToString("\n") { "Short line $it." }
		val document = render(
			"$filler\n![image](hammer://image/3)\nTHE-END",
			imageNames = mapOf(3 to "Cheshire"),
			images = mapOf(3 to ExportImage(3, "Cheshire", "png", encoded("png", width = 20, height = 40))),
		)

		assertInsideMargins(document)
		val drawn = drawnImages.single()
		assertEquals(3, drawn.pageIndex)
		assertEquals(0f to 1f, drawn.sourceTop to drawn.sourceBottom, "the image was cut across pages")
		assertEquals(STORY_PAGE_PADDING.top.value, drawn.y, 0.5f)
		assertTrue(drawn.height <= STORY_PAGE_SIZE.height.value * 0.6f + 0.5f, "image ${drawn.height}pt tall")
		assertEquals(3, document.textRuns.single { it.text.contains("THE-END") }.pageIndex)
	}

	@Test
	fun `an image is centred in the text column`() {
		renderImage(encoded("png"), "png")

		val drawn = drawnImages.single()
		val contentWidth = STORY_PAGE_SIZE.width.value - STORY_PAGE_PADDING.left.value - STORY_PAGE_PADDING.right.value
		assertEquals(contentWidth * 0.7f, drawn.width, 0.5f)
		assertEquals(drawn.width / 2, drawn.height, 0.5f)
		assertEquals(STORY_PAGE_SIZE.width.value / 2, drawn.x + drawn.width / 2, 0.5f)
	}

	private fun preparedImage(bytes: ByteArray, extension: String, width: Float, maxHeight: Float): ProseLayout.Image {
		val metrics = defaultPdfDriverFactory().create(PdfMetadata(), emptyList())
		val prose = try {
			val picture = prosePicture(ExportImage(1, "Cat", extension, bytes))
			prepareProse(
				"![image](hammer://image/1)",
				ProseColors(),
				metrics.fontMetrics,
				width,
				imageName = { "Cat" },
				picture = { picture },
				maxImageHeight = maxHeight,
			)
		} finally {
			metrics.close()
		}
		assertIs<ProseBlock.Image>(prose.blocks.single())
		return assertIs(prose.layouts.single())
	}

	@Test
	fun `an image spans seven tenths of the text width, centred, at its own aspect ratio`() {
		val layout = preparedImage(encoded("png", width = 40, height = 20), "png", width = 400f, maxHeight = 1000f)

		assertEquals(280f, layout.width, 0.01f)
		assertEquals(140f, layout.height, 0.01f)
		assertEquals(60f, layout.inset, 0.01f)
	}

	@Test
	fun `a tall image shrinks to the height cap, keeping its aspect ratio`() {
		val layout = preparedImage(encoded("png", width = 20, height = 80), "png", width = 400f, maxHeight = 500f)

		assertEquals(500f, layout.height, 0.01f)
		assertEquals(125f, layout.width, 0.01f)
		assertEquals(137.5f, layout.inset, 0.01f)
	}

	@Test
	fun `an svg keeps the aspect ratio of its view box`() {
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><rect width="20" height="10"/></svg>"""
		val layout = preparedImage(svg.encodeToByteArray(), "svg", width = 400f, maxHeight = 1000f)

		assertEquals(280f, layout.width, 0.01f)
		assertEquals(140f, layout.height, 0.01f)
	}

	@Test
	fun `only an image with a readable header has a picture`() {
		assertNotNull(prosePicture(ExportImage(1, "Cat", "PNG", encoded("png"))))
		assertNotNull(prosePicture(ExportImage(1, "Cat", "jpeg", encoded("jpg"))))
		assertNotNull(prosePicture(ExportImage(1, "Cat", "webp", webp())))
		assertNull(prosePicture(ExportImage(1, "Cat", "png", encoded("jpg").copyOf(1))))
	}

	@Test
	fun `an image with an extension Hammer does not allow has no picture`() {
		assertNull(prosePicture(ExportImage(1, "Cat", "bmp", encoded("bmp"))))
	}

	@Test
	fun `a jpeg turned by its exif orientation keeps its upright aspect ratio`() {
		val turned = withExifOrientation(encoded("jpg", width = 40, height = 20), orientation = 6)

		val picture = assertIs<ProsePicture.Raster>(prosePicture(ExportImage(1, "Cat", "jpg", turned)))

		assertEquals(2f, picture.aspectRatio, 0.01f)
		assertEquals(20 to 40, assertNotNull(readImageInfo(picture.bytes)).let { it.widthPx to it.heightPx })
	}

	@Test
	fun `an upright jpeg is drawn from its own bytes`() {
		val jpeg = withExifOrientation(encoded("jpg"), orientation = 1)

		val picture = assertIs<ProsePicture.Raster>(prosePicture(ExportImage(1, "Cat", "jpg", jpeg)))

		assertSame(jpeg, picture.bytes)
	}

	private class DrawnImage(
		val pageIndex: Int,
		val x: Float,
		val y: Float,
		val width: Float,
		val height: Float,
		val sourceTop: Float,
		val sourceBottom: Float,
	)

	/** The platform backend, noting every image it is asked to draw. */
	private class ImageWatchingFactory(private val drawn: MutableList<DrawnImage>) : PdfDriverFactory {
		private val delegate = defaultPdfDriverFactory()

		override fun create(metadata: PdfMetadata, customFonts: List<PdfFont.Custom>): PdfDriver {
			val driver = delegate.create(metadata, customFonts)
			return object : PdfDriver by driver {
				var pageIndex = -1

				override fun beginPage(size: PageSize): PdfCanvas {
					val page = ++pageIndex
					val canvas = driver.beginPage(size)
					return object : PdfCanvas by canvas {
						override fun drawImage(
							bytes: ByteArray,
							x: Float,
							y: Float,
							width: Float,
							height: Float,
							contentScale: ContentScale,
							sourceTop: Float,
							sourceBottom: Float,
							allowDownScale: Boolean,
							altText: String?,
						) {
							drawn += DrawnImage(page, x, y, width, height, sourceTop, sourceBottom)
							canvas.drawImage(
								bytes, x, y, width, height, contentScale, sourceTop, sourceBottom, allowDownScale, altText,
							)
						}
					}
				}
			}
		}
	}

	private companion object {
		val IMAGE_SUBTYPE = Regex("""/Subtype\s*/Image\b""")
	}
}
