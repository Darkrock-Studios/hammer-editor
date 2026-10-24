import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projecthome.ExportFormatChoice
import com.darkrockstudios.apps.hammer.common.projecthome.ExportOptionsDialogContent
import com.darkrockstudios.apps.hammer.common.projecthome.exportFormatChoices
import com.darkrockstudios.apps.hammer.settings_plugins_header
import okio.BufferedSink
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportOptionsDialogTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private val entries = listOf(
		ExportableScene(id = 1, name = "Scene 1", isGroup = false, depth = 0),
		ExportableScene(id = 2, name = "Scene 2", isGroup = false, depth = 0),
	)

	@Test
	fun `Confirming with every scene selected exports without a limit`() {
		var options by mutableStateOf(ExportOptions(sceneIds = setOf(1, 2)))
		var confirmed: ExportOptions? = null

		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = options,
					formats = exportFormatChoices(),
					exportableScenes = entries,
					onOptionsChanged = { options = it },
					onCancel = {},
					onConfirm = { confirmed = it },
					onShowHelp = {},
				)
			}
		}

		compose.onNodeWithText("Export").performClick()
		compose.waitForIdle()

		assertNull(
			confirmed?.sceneIds,
			"A full selection must export the entire story, keeping empty chapter groups",
		)
	}

	@Test
	fun `Confirming a partial selection keeps the scene limit`() {
		var options by mutableStateOf(ExportOptions(sceneIds = setOf(2)))
		var confirmed: ExportOptions? = null

		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = options,
					formats = exportFormatChoices(),
					exportableScenes = entries,
					onOptionsChanged = { options = it },
					onCancel = {},
					onConfirm = { confirmed = it },
					onShowHelp = {},
				)
			}
		}

		compose.onNodeWithText("Export").performClick()
		compose.waitForIdle()

		assertEquals(setOf(2), confirmed?.sceneIds)
	}

	@Test
	fun `A project without leaf scenes offers no scene limit`() {
		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = ExportOptions(),
					formats = exportFormatChoices(),
					exportableScenes = listOf(
						ExportableScene(id = 9, name = "Empty Group", isGroup = true, depth = 0),
					),
					onOptionsChanged = {},
					onCancel = {},
					onConfirm = {},
					onShowHelp = {},
				)
			}
		}

		compose.onNodeWithText("Limit to specific scenes").assertDoesNotExist()
	}

	private fun setContent(initial: ExportOptions, onChanged: (ExportOptions) -> Unit = {}) {
		var options by mutableStateOf(initial)
		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = options,
					formats = exportFormatChoices(),
					exportableScenes = entries,
					onOptionsChanged = {
						options = it
						onChanged(it)
					},
					onCancel = {},
					onConfirm = {},
					onShowHelp = {},
				)
			}
		}
	}

	@Test
	fun `Formatting toggles update the options`() {
		var latest = ExportOptions(format = BuiltInExportFormat.DOCX)
		setContent(latest) { latest = it }

		compose.onNodeWithText("Number chapter titles").performClick()
		compose.onNodeWithText("Keep blank lines between paragraphs").performClick()
		compose.waitForIdle()

		assertEquals(true, latest.numberChapters)
		assertEquals(false, latest.keepBlankLines)
	}

	@Test
	fun `Word export offers a font choice`() {
		var latest = ExportOptions(format = BuiltInExportFormat.DOCX)
		setContent(latest) { latest = it }

		compose.onNodeWithText("Georgia").performClick()
		compose.onNodeWithText("Times New Roman").performClick()
		compose.waitForIdle()

		assertEquals(ExportFont.TimesNewRoman, latest.font)
	}

	@Test
	fun `PDF export offers no font choice`() {
		setContent(ExportOptions(format = BuiltInExportFormat.PDF))

		compose.onNodeWithText("Georgia").assertDoesNotExist()
		compose.onNodeWithText("Number chapter titles").assertExists()
	}

	@Test
	fun `EPUB export offers chapter numbering and a font`() {
		setContent(ExportOptions(format = BuiltInExportFormat.EPUB))

		compose.onNodeWithText("Number chapter titles").assertExists()
		compose.onNodeWithText("Georgia").assertExists()
	}

	@Test
	fun `Markdown export offers only numbering`() {
		setContent(ExportOptions(format = BuiltInExportFormat.MARKDOWN))

		compose.onNodeWithText("Number chapter titles").assertExists()
		compose.onNodeWithText("Georgia").assertDoesNotExist()
		compose.onNodeWithText("Keep blank lines between paragraphs").assertDoesNotExist()
	}

	@Test
	fun `A contributed format shows its own label, or its extension without one`() {
		var choices: List<ExportFormatChoice> = emptyList()
		compose.setContent {
			choices = exportFormatChoices(
				StoryExporterRegistry(listOf(FakeExporter("a.fdx", "fdx"), FakeExporter("b.txt", "txt", "Plain text"))).exporters,
			)
		}
		compose.waitForIdle()

		assertEquals("FDX", choices.single { it.formatId == "a.fdx" }.label)
		assertEquals("Plain text", choices.single { it.formatId == "b.txt" }.label)
	}

	private class FakeExporter(
		override val formatId: String,
		override val fileExtension: String,
		override val label: String? = null,
	) : StoryExporter {
		override val mimeType = "text/plain"
		override fun render(sink: BufferedSink, input: ExportInput) = Unit
	}
}
