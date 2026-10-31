package data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ManuscriptContact
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
			Roses are red,
			violets are blue.


			After a blank run.

			![image](hammer://image/3)

			![image](hammer://image/4)
		""".trimIndent()
		val images = mapOf(
			3 to ExportImage(3, "Cat & <Dog>", "png", encoded("png")),
			4 to ExportImage(4, "Dog", "jpg", encoded("jpg")),
			5 to ExportImage(5, "Cover", "png", encoded("png", 40, 60)),
		)
		val contact = ManuscriptContact(
			legalName = "Jane Q. Writer",
			address = "12 Main St & Co\nSpringfield, IL 62701",
			phone = "555-0100",
			email = "jane@example.com",
		)

		fun write(name: String, projectData: ProjectData, chapters: List<StoryChapter>, options: ExportOptions, contact: ManuscriptContact) {
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
				options = options,
				imageNames = images.mapValues { it.value.name },
				images = images,
				contact = contact,
			)
			File(dir, name).writeBytes(buffer.readByteArray())
		}

		val rich = ProjectData(authorName = "Some One", coverImageId = 5)
		val chapters = listOf(StoryChapter("One", listOf(markdown, "", "Second scene.")), StoryChapter("Two", "Second."))
		write("novel-modern.docx", rich, chapters, ExportOptions(numberChapters = true), contact)
		write("novel-classic.docx", rich, chapters, ExportOptions(manuscriptClassic = true), ManuscriptContact())
		write("story-modern.docx", rich, chapters, ExportOptions(manuscriptShortStory = true), ManuscriptContact())
		write(
			"story-classic.docx",
			rich,
			listOf(StoryChapter("Only", listOf(markdown, "Second scene."))),
			ExportOptions(manuscriptShortStory = true, manuscriptClassic = true),
			contact,
		)
		write("minimal-novel.docx", ProjectData(), emptyList(), ExportOptions(), ManuscriptContact())
		write("minimal-story.docx", ProjectData(), emptyList(), ExportOptions(manuscriptShortStory = true), ManuscriptContact())
	}
}
