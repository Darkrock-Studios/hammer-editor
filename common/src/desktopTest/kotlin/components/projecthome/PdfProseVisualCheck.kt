package components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.components.projecthome.ExportStrings
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
			The opening paragraph of a chapter carries a first-line indent, as does every other. It runs long enough to wrap across several lines so the wrapping behaviour around the indent can be seen clearly on the page.

			The second paragraph sits one blank line below, because that is the blank line the author typed. It also runs long enough to wrap, which shows that only the first line is indented and continuation lines return to the margin.

			A third paragraph continues the prose with **bold**, *italic*, and ~~struck~~ words along the way to confirm inline styling still works.

			– Good evening, madame. I did not mean to disturb you.
			– Think nothing of it, she said. I am only doing my work.
			– Then perhaps you can tell me where the old medicines are kept, said the visitor, whose question ran on long enough to wrap onto a second line.

			These lines of dialogue each stand on their own, exactly as they do in the editor.


			Two blank lines above this one open a wider gap, the way the author asked for it.

			---

			After a scene break the prose continues.

			> A quoted passage keeps its lines,
			> one under the next.

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
			writeStoryAsPdf(
				this,
				"Indent Check",
				projectData,
				chapters,
				ExportStrings(contentsTitle = "Contents", authorByline = "by Test Author"),
			)
		}
		println("Wrote $out")
	}
}
