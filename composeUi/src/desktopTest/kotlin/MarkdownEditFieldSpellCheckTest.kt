import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onRoot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.notes.ViewNote
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.notes.ViewNoteUi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class MarkdownEditFieldSpellCheckTest {

	@get:Rule
	val compose = createComposeRule()

	private fun mount(markdown: String) {
		compose.setContent {
			KoinApplicationPreview {
				MarkdownEditField(
					initialMarkdown = markdown,
					onMarkdownChanged = {},
					testTag = EDITOR_TAG,
				)
			}
		}
	}

	// The squiggle is the only pure red the editor draws.
	private fun squigglePixels(node: SemanticsNodeInteraction = compose.onNodeWithTag(EDITOR_TAG)): Int {
		val pixels = node.captureToImage().toPixelMap()
		var count = 0
		for (x in 0 until pixels.width) {
			for (y in 0 until pixels.height) {
				val c = pixels[x, y]
				if (c.red > 0.8f && c.green < 0.3f && c.blue < 0.3f) count++
			}
		}
		return count
	}

	@Test
	fun `a misspelling in the initial text is underlined`() {
		mount("This sentense has a tyypo in it.")

		compose.waitUntil(timeoutMillis = 10_000) { squigglePixels() > 0 }
	}

	@Test
	fun `correctly spelled initial text is not underlined`() {
		mount("This sentence has no typo in it.")

		compose.mainClock.advanceTimeBy(2_000)
		compose.waitForIdle()
		assertEquals(0, squigglePixels())
	}

	@OptIn(ExperimentalSharedTransitionApi::class)
	@Test
	fun `a misspelling in a note being edited is underlined`() {
		val text = "This sentense has a tyypo in it."
		val component = EditingViewNote(text)
		compose.setContent {
			KoinApplicationPreview {
				SharedTransitionLayout {
					AnimatedVisibility(visible = true) {
						ViewNoteUi(
							component = component,
							modifier = Modifier,
							rootSnackbar = rememberRootSnackbarHostState(),
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@AnimatedVisibility,
						)
					}
				}
			}
		}

		compose.waitUntil(timeoutMillis = 10_000) { squigglePixels(compose.onRoot()) > 0 }
	}

	private class EditingViewNote(text: String) : ViewNote {
		private val projectDef = ProjectDef(
			name = "Test",
			path = HPath(name = "Test", path = "/", isAbsolute = true),
		)
		override val state: Value<ViewNote.State> = MutableValue(
			ViewNote.State(
				projectDef = projectDef,
				note = NoteContent(id = 1, created = Clock.System.now(), content = text),
				isEditing = true,
			)
		)
		override val noteText: Value<String> = MutableValue(text)

		override fun discardEdit() {}
		override fun onContentChanged(newContent: String) {}
		override fun onTagsChanged(newTags: Set<String>) {}
		override suspend fun removeTag(tag: String) {}
		override fun showGlobalSearchForTag(tag: String) {}
		override suspend fun deleteNote(id: Int) {}
		override fun confirmDelete() {}
		override fun dismissConfirmDelete() {}
		override suspend fun storeNoteUpdate() {}
		override fun closeNote() {}
		override fun beginEdit() {}
		override fun isEditingAndDirty(): Boolean = false
		override fun confirmDiscard() {}
		override fun cancelDiscard() {}
		override fun confirmClose() {}
		override fun cancelClose() {}
		override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
	}

	private companion object {
		const val EDITOR_TAG = "markdown-edit-field"
	}
}
