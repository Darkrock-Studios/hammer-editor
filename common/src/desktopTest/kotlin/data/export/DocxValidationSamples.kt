package data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.writeStoryAsDocx
import okio.Buffer
import org.junit.jupiter.api.Test
import java.io.File

// Writes sample .docx files for scripts/validate-docx.sh --samples; does nothing unless DOCX_SAMPLES_DIR is set.
class DocxValidationSamples {
	@Test
	fun `write samples`() {
		val dir = File(System.getenv("DOCX_SAMPLES_DIR") ?: return).apply { mkdirs() }
		val markdown = """
			Plain paragraph with **bold**, *italic*, ~~strike~~ and `code` & <specials> "quotes" 'é ü 中文'.
			A line that runs on.

			# Heading one
			## Heading two

			- bullet
			  - nested
			1. one
			2. two

			> A quote

			```
			code block
			```

			---

			| a | b |
			|---|---|
			| 1 | 2 |

			[a link](https://example.com) and another [link](https://example.org?a=1&b=2)

			![image](hammer://image/3)

			![image](hammer://image/4)
		""".trimIndent()
		val images = mapOf(
			3 to ExportImage(3, "Cat & <Dog>", "png", encoded("png")),
			4 to ExportImage(4, "Dog", "jpg", encoded("jpg")),
			5 to ExportImage(5, "Cover", "png", encoded("png", 40, 60)),
		)

		fun write(name: String, projectData: ProjectData, chapters: List<StoryChapter>) {
			val buffer = Buffer()
			writeStoryAsDocx(
				sink = buffer,
				projectName = "Sample & <Story>",
				projectData = projectData,
				chapters = chapters,
				strings = ExportStrings(
					contentsTitle = "Contents",
					authorByline = projectData.authorName?.let { "by $it" },
					wordCount = "about 12,500 words",
					endMarker = "END",
				),
				options = ExportOptions(numberChapters = true),
				imageNames = images.mapValues { it.value.name },
				images = images,
			)
			File(dir, name).writeBytes(buffer.readByteArray())
		}

		write(
			"rich.docx",
			ProjectData(
				authorName = "Someone",
				coverImageId = 5,
				theme = ProjectTheme(primary = "FF336699", secondary = "FF993366"),
			),
			listOf(StoryChapter("One", markdown), StoryChapter("Two", "Second.")),
		)
		write("minimal.docx", ProjectData(), emptyList())
	}
}
