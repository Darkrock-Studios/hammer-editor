import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExporterSource
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.data.ManuscriptContact
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projecthome.EXPORT_CONTACT_ADDRESS_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.EXPORT_CONTACT_EMAIL_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.EXPORT_CONTACT_LEGAL_NAME_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.EXPORT_CONTACT_PHONE_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.ExportFormatChoice
import com.darkrockstudios.apps.hammer.common.projecthome.ExportOptionsDialogContent
import com.darkrockstudios.apps.hammer.common.projecthome.SCENE_SELECTOR_MASTER_TOGGLE_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.exportFormatChoices
import com.darkrockstudios.apps.hammer.common.projecthome.sceneSelectorSceneTag
import com.darkrockstudios.apps.hammer.settings_plugins_header
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okio.BufferedSink
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
					contact = ManuscriptContact(),
					onOptionsChanged = { options = it },
					onContactChanged = {},
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
					contact = ManuscriptContact(),
					onOptionsChanged = { options = it },
					onContactChanged = {},
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
					contact = ManuscriptContact(),
					onOptionsChanged = {},
					onContactChanged = {},
					onCancel = {},
					onConfirm = {},
					onShowHelp = {},
				)
			}
		}

		compose.onNodeWithText("Limit to specific scenes").assertDoesNotExist()
	}

	private fun setContent(
		initial: ExportOptions,
		initialContact: ManuscriptContact = ManuscriptContact(),
		onContactChanged: (ManuscriptContact) -> Unit = {},
		onChanged: (ExportOptions) -> Unit = {},
	) {
		var options by mutableStateOf(initial)
		var contact by mutableStateOf(initialContact)
		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = options,
					contact = contact,
					formats = exportFormatChoices(),
					exportableScenes = entries,
					onOptionsChanged = {
						options = it
						onChanged(it)
					},
					onContactChanged = {
						contact = it
						onContactChanged(it)
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
		var latest = ExportOptions(format = BuiltInExportFormat.RTF)
		setContent(latest) { latest = it }

		compose.onNodeWithText("Number chapter titles").performClick()
		compose.onNodeWithText("Keep blank lines between paragraphs").performClick()
		compose.waitForIdle()

		assertEquals(true, latest.numberChapters)
		assertEquals(false, latest.keepBlankLines)
	}

	@Test
	fun `RTF export offers a font choice`() {
		var latest = ExportOptions(format = BuiltInExportFormat.RTF)
		setContent(latest) { latest = it }

		compose.onNodeWithText("Georgia").performClick()
		compose.onNodeWithText("Times New Roman").performClick()
		compose.waitForIdle()

		assertEquals(ExportFont.TimesNewRoman, latest.font)
	}

	@Test
	fun `Word export offers the manuscript layout and contact, not a font or blank lines`() {
		setContent(ExportOptions(format = BuiltInExportFormat.DOCX))

		compose.onNodeWithText("Word manuscript (.docx)").assertExists()
		compose.onNodeWithText("Number chapter titles").assertExists()
		compose.onNodeWithText("Short story layout").assertExists()
		compose.onNodeWithText("Classic (Courier)").assertExists()
		CONTACT_FIELD_TAGS.forEach { compose.onNodeWithTag(it).assertExists() }
		compose.onNodeWithText("Georgia").assertDoesNotExist()
		compose.onNodeWithText("Keep blank lines between paragraphs").assertDoesNotExist()
	}

	@Test
	fun `Manuscript toggles update their own option`() {
		var latest = ExportOptions(format = BuiltInExportFormat.DOCX)
		setContent(latest) { latest = it }

		compose.onNodeWithText("Short story layout").performScrollTo().performClick()
		compose.waitForIdle()
		assertEquals(true, latest.manuscriptShortStory)
		assertEquals(false, latest.manuscriptClassic)

		compose.onNodeWithText("Classic (Courier)").performScrollTo().performClick()
		compose.waitForIdle()
		assertEquals(true, latest.manuscriptShortStory)
		assertEquals(true, latest.manuscriptClassic)
	}

	@Test
	fun `Typing in a contact field updates only that field`() {
		val initial = ManuscriptContact(
			legalName = "Old Name",
			address = "Old Street",
			phone = "555",
			email = "old@example.com",
		)
		var latest = initial
		setContent(ExportOptions(format = BuiltInExportFormat.DOCX), initial, onContactChanged = { latest = it })

		fun replaceText(tag: String, text: String) {
			compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
			compose.waitForIdle()
		}

		replaceText(EXPORT_CONTACT_LEGAL_NAME_TAG, "Jane Writer")
		assertEquals(initial.copy(legalName = "Jane Writer"), latest)

		replaceText(EXPORT_CONTACT_ADDRESS_TAG, "1 Lane\nTown")
		assertEquals(initial.copy(legalName = "Jane Writer", address = "1 Lane\nTown"), latest)

		replaceText(EXPORT_CONTACT_PHONE_TAG, "555-0100")
		assertEquals(
			ManuscriptContact("Jane Writer", "1 Lane\nTown", "555-0100", initial.email),
			latest,
		)

		replaceText(EXPORT_CONTACT_EMAIL_TAG, "jane@example.com")
		assertEquals(
			ManuscriptContact("Jane Writer", "1 Lane\nTown", "555-0100", "jane@example.com"),
			latest,
		)
	}

	@Test
	fun `Other formats offer no manuscript controls`() {
		var options by mutableStateOf(ExportOptions(format = BuiltInExportFormat.EPUB))
		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = options,
					contact = ManuscriptContact(),
					formats = exportFormatChoices(),
					exportableScenes = entries,
					onOptionsChanged = {},
					onContactChanged = {},
					onCancel = {},
					onConfirm = {},
					onShowHelp = {},
				)
			}
		}

		listOf(
			BuiltInExportFormat.EPUB,
			BuiltInExportFormat.PDF,
			BuiltInExportFormat.RTF,
			BuiltInExportFormat.MARKDOWN,
		).forEach { format ->
			options = ExportOptions(format = format)
			compose.waitForIdle()

			compose.onNodeWithText("Short story layout").assertDoesNotExist()
			compose.onNodeWithText("Classic (Courier)").assertDoesNotExist()
			CONTACT_FIELD_TAGS.forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
		}
	}

	@Test
	fun `Every option control is disabled while the export is working`() {
		var options by mutableStateOf(ExportOptions(format = BuiltInExportFormat.DOCX, sceneIds = setOf(1)))
		var changed = false
		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = options,
					contact = ManuscriptContact(),
					formats = exportFormatChoices(),
					exportableScenes = entries,
					onOptionsChanged = { changed = true },
					onContactChanged = { changed = true },
					onCancel = {},
					onConfirm = {},
					onShowHelp = {},
					working = true,
				)
			}
		}

		listOf(
			"Word manuscript (.docx)",
			"Number chapter titles",
			"Short story layout",
			"Classic (Courier)",
			"Limit to specific scenes",
		).forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
		CONTACT_FIELD_TAGS.forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
		compose.onNodeWithTag(SCENE_SELECTOR_MASTER_TOGGLE_TAG).assertIsNotEnabled()
		compose.onNodeWithTag(sceneSelectorSceneTag(1)).assertIsNotEnabled()

		options = ExportOptions(format = BuiltInExportFormat.RTF)
		compose.waitForIdle()

		listOf(
			"RTF (.rtf)",
			"Georgia",
			"Keep blank lines between paragraphs",
		).forEach { compose.onNodeWithText(it).assertIsNotEnabled() }

		compose.onNodeWithText("Keep blank lines between paragraphs").performClick()
		compose.waitForIdle()
		assertFalse(changed, "Clicking a disabled option must not change it")
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
	fun `A format no longer offered is replaced by the default it shows`() {
		var confirmed: ExportOptions? = null
		compose.setContent {
			KoinApplicationPreview {
				ExportOptionsDialogContent(
					options = ExportOptions(format = "gone.fdx"),
					formats = exportFormatChoices(),
					exportableScenes = entries,
					contact = ManuscriptContact(),
					onOptionsChanged = {},
					onContactChanged = {},
					onCancel = {},
					onConfirm = { confirmed = it },
					onShowHelp = {},
				)
			}
		}

		compose.onNodeWithText("EPub (.epub)").assertExists()
		compose.onNodeWithText("Georgia").assertExists()
		compose.onNodeWithText("Export").performClick()
		compose.waitForIdle()

		assertEquals(BuiltInExportFormat.EPUB, confirmed?.format)
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

	@Test
	fun `The format choices follow plugins added while they show`() {
		val formats = MutableStateFlow<List<StoryExporter>>(emptyList())
		val plugins = object : ExporterSource {
			override val changes = formats.map { }
			override fun exporters(disabledPlugins: Set<String>) = formats.value
		}
		val registry = StoryExporterRegistry(emptyList(), listOf(plugins))
		var choices: List<ExportFormatChoice> = emptyList()
		compose.setContent {
			choices = exportFormatChoices(registry, disabledPlugins = emptySet())
		}
		compose.waitForIdle()
		assertNull(choices.firstOrNull { it.formatId == "a.fdx" })

		formats.value = listOf(FakeExporter("a.fdx", "fdx"))
		compose.waitForIdle()

		assertEquals("FDX", choices.single { it.formatId == "a.fdx" }.label)
	}

	private companion object {
		val CONTACT_FIELD_TAGS = listOf(
			EXPORT_CONTACT_LEGAL_NAME_TAG,
			EXPORT_CONTACT_ADDRESS_TAG,
			EXPORT_CONTACT_PHONE_TAG,
			EXPORT_CONTACT_EMAIL_TAG,
		)
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
