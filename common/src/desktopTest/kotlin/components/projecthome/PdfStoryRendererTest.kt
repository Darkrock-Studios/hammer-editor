package components.projecthome

import com.conamobile.pdfkmp.PdfDocument
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projecthome.ExportStrings
import com.darkrockstudios.apps.hammer.common.components.projecthome.STORY_PAGE_PADDING
import com.darkrockstudios.apps.hammer.common.components.projecthome.STORY_PAGE_SIZE
import com.darkrockstudios.apps.hammer.common.components.projecthome.StoryChapter
import com.darkrockstudios.apps.hammer.common.components.projecthome.buildStoryPdf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfStoryRendererTest {

	private fun render(markdown: String): PdfDocument = buildStoryPdf(
		projectName = "Story",
		projectData = ProjectData(authorName = "Author"),
		chapters = listOf(StoryChapter("Chapter", markdown)),
		strings = ExportStrings(contentsTitle = "Contents", authorByline = "by Author"),
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
}
