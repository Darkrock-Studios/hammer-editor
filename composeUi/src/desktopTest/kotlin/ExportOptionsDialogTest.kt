import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projecthome.ExportOptionsDialogContent
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
		var latest = ExportOptions(format = ExportFormat.Docx)
		setContent(latest) { latest = it }

		compose.onNodeWithText("Number chapter titles").performClick()
		compose.onNodeWithText("Keep blank lines between paragraphs").performClick()
		compose.waitForIdle()

		assertEquals(true, latest.numberChapters)
		assertEquals(false, latest.keepBlankLines)
	}

	@Test
	fun `Word export offers a font choice`() {
		var latest = ExportOptions(format = ExportFormat.Docx)
		setContent(latest) { latest = it }

		compose.onNodeWithText("Georgia").performClick()
		compose.onNodeWithText("Times New Roman").performClick()
		compose.waitForIdle()

		assertEquals(ExportFont.TimesNewRoman, latest.font)
	}

	@Test
	fun `PDF export offers no font choice`() {
		setContent(ExportOptions(format = ExportFormat.Pdf))

		compose.onNodeWithText("Georgia").assertDoesNotExist()
		compose.onNodeWithText("Number chapter titles").assertExists()
	}

	@Test
	fun `EPUB export offers chapter numbering and a font`() {
		setContent(ExportOptions(format = ExportFormat.Epub))

		compose.onNodeWithText("Number chapter titles").assertExists()
		compose.onNodeWithText("Georgia").assertExists()
	}

	@Test
	fun `Markdown export offers only numbering`() {
		setContent(ExportOptions(format = ExportFormat.Markdown))

		compose.onNodeWithText("Number chapter titles").assertExists()
		compose.onNodeWithText("Georgia").assertDoesNotExist()
		compose.onNodeWithText("Keep blank lines between paragraphs").assertDoesNotExist()
	}
}
