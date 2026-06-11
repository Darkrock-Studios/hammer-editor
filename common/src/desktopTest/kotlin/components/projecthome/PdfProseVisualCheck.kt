package components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.components.projecthome.StoryChapter
import com.darkrockstudios.apps.hammer.common.components.projecthome.writeStoryAsPdf
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** Manual visual check: renders a sample PDF to a real temp file for eyeballing. Not part of CI. */
class PdfProseVisualCheck {

	@Test
	@EnabledIfEnvironmentVariable(named = "PDF_VISUAL_CHECK", matches = "1")
	fun `render sample pdf`() {
		val markdown = """
			The opening paragraph of a chapter sits flush against the margin, as is the convention in book typesetting. It runs long enough to wrap across several lines so the wrapping behaviour around the indent can be seen clearly on the page.

			The second paragraph begins with a first-line indent, marking the paragraph break without a blank line. It also runs long enough to wrap, which shows that only the first line is indented and continuation lines return to the margin.

			A third paragraph continues the prose with the same indent, with **bold**, *italic*, and ~~struck~~ words along the way to confirm inline styling still works.

			---

			After a scene break the next paragraph is flush again, signalling a fresh section to the reader.

			And the one after it is indented once more.

			## A Secondary Heading

			A paragraph under an in-chapter h2 heading, which takes the secondary accent color, with a [link](https://example.com) that should take the primary accent.
		""".trimIndent()

		val chapters = listOf(StoryChapter("A Sample Chapter", markdown))
		val out = System.getProperty("java.io.tmpdir").toPath() / "prose-check.pdf"
		val projectData = ProjectData(
			authorName = "Test Author",
			theme = ProjectTheme(primary = "#FF6750A4", secondary = "#FF7D5260"),
		)
		FileSystem.SYSTEM.write(out) {
			writeStoryAsPdf(this, "Indent Check", projectData, chapters)
		}
		println("Wrote $out")
	}
}
