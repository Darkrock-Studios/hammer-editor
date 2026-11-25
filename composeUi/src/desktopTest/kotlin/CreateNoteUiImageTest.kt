import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.notes.CreateNote
import com.darkrockstudios.apps.hammer.common.compose.LocalImageProvider
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import com.darkrockstudios.apps.hammer.common.notes.CreateNoteUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.texteditor.richstyle.ImageBlockResource
import com.darkrockstudios.texteditor.richstyle.ImageProvider
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The create-note screen offers the same image button as editing a note. */
class CreateNoteUiImageTest {

	@get:Rule
	val compose = createComposeRule()

	private class FakeCreateNote : CreateNote {
		override val state: Value<CreateNote.State> = MutableValue(CreateNote.State())
		private val _noteText = MutableValue("")
		override val noteText: Value<String> = _noteText

		var picks = 0
		override suspend fun pickImageForText(): ImageDef {
			picks++
			return ImageDef(id = 3, name = "Cat", fileExtension = "png")
		}

		override fun onTextChanged(newText: String) {
			_noteText.value = newText
		}

		override suspend fun createNote(noteText: String, tags: Set<String>) = NoteError.NONE
		override fun closeCreate() {}
		override fun confirmDiscard() {}
		override fun cancelDiscard() {}
		override fun clearText() {}
		override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
	}

	/** Inside a project images resolve; an image is only offered there. */
	private val imageProvider = object : ImageProvider {
		override fun resolve(source: String): State<ImageBlockResource> = mutableStateOf(ImageBlockResource.Loading)
	}

	@Test
	fun `a new note can have an image inserted into its text`() {
		val component = FakeCreateNote()
		compose.setContent {
			KoinApplicationPreview {
				CompositionLocalProvider(LocalImageProvider provides imageProvider) {
					CreateNoteUi(component, Modifier, rememberRootSnackbarHostState())
				}
			}
		}

		compose.onNodeWithTag(MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG, useUnmergedTree = true).performClick()
		compose.waitForIdle()

		assertEquals(1, component.picks)
		val text = component.noteText.value
		assertTrue(text.contains(ImageReferenceSyntax.reference(3)), text)
	}
}
