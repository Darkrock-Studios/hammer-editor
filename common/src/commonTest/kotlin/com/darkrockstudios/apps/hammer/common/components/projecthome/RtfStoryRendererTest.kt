package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.RtfSplitStrategy
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import com.darkrockstudios.apps.hammer.common.data.importer.RtfStoryImporter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RtfStoryRendererTest {

	private fun render(
		chapters: List<StoryChapter>,
		projectName: String = "Test Project",
		projectData: ProjectData = ProjectData(authorName = "Test Author"),
	): String {
		val buffer = Buffer()
		val author = projectData.authorName?.takeIf { it.isNotBlank() }
		writeStoryAsRtf(
			sink = buffer,
			projectName = projectName,
			projectData = projectData,
			chapters = chapters,
			strings = ExportStrings(contentsTitle = "Contents", authorByline = author?.let { "by $it" }),
		)
		return buffer.readByteArray().decodeToString()
	}

	/** Counts brace nesting, ignoring the backslash-escaped `\{` and `\}` that appear in body text. */
	private fun braceBalance(rtf: String): Int {
		var depth = 0
		var i = 0
		while (i < rtf.length) {
			val c = rtf[i]
			when {
				c == '\\' -> i++ // skip the escaped/control character that follows
				c == '{' -> depth++
				c == '}' -> depth--
			}
			i++
		}
		return depth
	}

	@Test
	fun `output is a well-formed RTF document with balanced braces`() {
		val rtf = render(listOf(StoryChapter("Alpha", "Some text.")))

		assertTrue(rtf.startsWith("{\\rtf1"), "RTF must start with the {\\rtf1 signature")
		assertTrue(rtf.endsWith("}"), "RTF must close its root group")
		assertEquals(0, braceBalance(rtf), "Every RTF group must be balanced")
	}

	@Test
	fun `title page carries the project name and author byline`() {
		val rtf = render(
			listOf(StoryChapter("Alpha", "Body.")),
			projectName = "My Story",
			projectData = ProjectData(authorName = "Jane Doe"),
		)

		assertTrue(rtf.contains("My Story"), "Title should appear in the document")
		assertTrue(rtf.contains("by Jane Doe"), "Author byline should appear in the document")
		assertTrue(rtf.contains("\\title My Story"), "Title metadata should be present")
		assertTrue(rtf.contains("\\author Jane Doe"), "Author metadata should be present")
	}

	@Test
	fun `generator metadata identifies Hammer`() {
		val rtf = render(listOf(StoryChapter("Alpha", "Body.")))

		assertTrue(
			rtf.contains("{\\*\\generator Hammer "),
			"Generator metadata should mark Hammer as the source application",
		)
	}

	@Test
	fun `chapters are numbered and bookmarked for the contents links`() {
		val rtf = render(
			listOf(
				StoryChapter("Alpha", "First."),
				StoryChapter("Beta", "Second."),
			)
		)

		assertTrue(rtf.contains("1. Alpha"), "First chapter should be numbered")
		assertTrue(rtf.contains("2. Beta"), "Second chapter should be numbered")
		assertTrue(rtf.contains("\\*\\bkmkstart chapter1"), "Chapters need bookmark anchors")
		assertTrue(rtf.contains("HYPERLINK \\\\l \"chapter1\""), "Contents should link to chapter bookmarks")
	}

	@Test
	fun `markdown emphasis becomes RTF bold and italic`() {
		val rtf = render(listOf(StoryChapter("Alpha", "A **bold** and _italic_ word.")))

		assertTrue(rtf.contains("{\\b bold}"), "Strong markdown should become an RTF bold group")
		assertTrue(rtf.contains("{\\i italic}"), "Emphasis markdown should become an RTF italic group")
	}

	@Test
	fun `markdown strikethrough becomes an RTF strike group`() {
		val rtf = render(listOf(StoryChapter("Alpha", "This is ~~gone~~ now.")))

		assertTrue(rtf.contains("{\\strike gone}"), "GFM strikethrough should become an RTF strike group")
	}

	@Test
	fun `braces in prose are escaped so they do not open groups`() {
		val rtf = render(listOf(StoryChapter("Alpha", "Use {braces} and a back\\slash.")))

		assertTrue(rtf.contains("\\{braces\\}"), "Literal braces must be backslash-escaped")
		assertEquals(0, braceBalance(rtf), "Escaped braces must not unbalance the document")
	}

	@Test
	fun `theme accent colors populate the color table`() {
		// The primary accent colors chapter headings; a level-2 markdown heading exercises the secondary.
		val rtf = render(
			listOf(StoryChapter("Alpha", "## Section\n\nBody.")),
			projectData = ProjectData(
				authorName = "Jane",
				theme = ProjectTheme(primary = "#FF112233", secondary = "#FFAABBCC"),
			),
		)

		assertTrue(rtf.contains("\\red17\\green34\\blue51;"), "Primary accent should be in the color table")
		assertTrue(rtf.contains("\\red170\\green187\\blue204;"), "Secondary accent should be in the color table")
		assertTrue(rtf.contains("\\cf1"), "Headings should reference the primary color")
	}

	@Test
	fun `non-ascii characters are emitted as unicode escapes`() {
		val rtf = render(listOf(StoryChapter("Café", "Naïve résumé — café.")))

		assertTrue(rtf.contains("\\u233?"), "é (U+00E9) should be a \\u233 escape")
		assertTrue(rtf.contains("\\u8212?"), "em dash (U+2014) should be a \\u8212 escape")
	}

	@Test
	fun `rendered prose survives a round-trip through the RTF importer`() {
		val rtf = render(
			listOf(StoryChapter("Alpha", "The quick brown fox jumped.")),
			projectData = ProjectData(),
		)

		val preview = RtfStoryImporter().preview(
			sourceName = "story",
			content = rtf.encodeToByteArray(),
			options = ImportOptions(
				rtfSplitStrategy = RtfSplitStrategy.SingleScene,
				rtfChapterPattern = "",
				createChapterGroups = false,
			),
		)

		val scene = preview.items.single() as PreviewItem.Scene
		assertTrue(
			scene.markdown.contains("The quick brown fox jumped."),
			"Chapter body should survive the export/import round-trip, got: ${scene.markdown}",
		)
	}
}
