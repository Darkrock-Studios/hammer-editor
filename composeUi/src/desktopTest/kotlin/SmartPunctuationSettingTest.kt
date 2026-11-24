import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import com.darkrockstudios.apps.hammer.common.compose.ApplySmartPunctuation
import com.darkrockstudios.apps.hammer.common.compose.LocalSmartPunctuation
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.fakeSceneEditor
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SCENE_EDITOR_TEXT_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.texteditor.behaviors.SmartPunctuation
import com.darkrockstudios.texteditor.state.TextEditorState
import com.darkrockstudios.texteditor.state.rememberTextEditorState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SmartPunctuationSettingTest {

	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `toggling the setting adds and removes smart punctuation on an open editor`() {
		var enabled by mutableStateOf(false)
		lateinit var state: TextEditorState
		compose.setContent {
			state = rememberTextEditorState()
			CompositionLocalProvider(LocalSmartPunctuation provides enabled) {
				ApplySmartPunctuation(state)
			}
		}
		fun count() = state.editBehaviors.count { it is SmartPunctuation }

		compose.waitForIdle()
		assertEquals(0, count())

		enabled = true
		compose.waitForIdle()
		assertEquals(1, count())

		enabled = false
		compose.waitForIdle()
		assertEquals(0, count())
	}

	@Test
	fun `the scene editor makes an em dash when the setting is on`() =
		assertTyped(smartPunctuation = true, SCENE_EDITOR_TEXT_TAG, sceneEditor)

	@Test
	fun `the scene editor keeps two hyphens when the setting is off`() =
		assertTyped(smartPunctuation = false, SCENE_EDITOR_TEXT_TAG, sceneEditor)

	@Test
	fun `an edit field makes an em dash when the setting is on`() =
		assertTyped(smartPunctuation = true, FIELD_TAG, editField)

	private val sceneEditor: @Composable () -> Unit = {
		SceneEditorUi(fakeSceneEditor(markdown = ""), rememberRootSnackbarHostState())
	}

	private val editField: @Composable () -> Unit = {
		MarkdownEditField(initialMarkdown = "", onMarkdownChanged = {}, testTag = FIELD_TAG)
	}

	/** Types `a--` a character at a time into the editor under [editorTag] and checks what it became. */
	private fun assertTyped(smartPunctuation: Boolean, editorTag: String, content: @Composable () -> Unit) {
		compose.setContent {
			KoinApplicationPreview {
				CompositionLocalProvider(LocalSmartPunctuation provides smartPunctuation) {
					content()
				}
			}
		}
		compose.waitForIdle()
		val editor = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(editorTag)))
		HYPHENS.forEach { editor.performTextInput(it.toString()) }
		compose.waitForIdle()

		val expected = if (smartPunctuation) EM_DASH else HYPHENS
		editor.assertTextSatisfies { it.text.trim() == expected }
	}

	private companion object {
		const val FIELD_TAG = "smart-punctuation-edit-field"
		const val HYPHENS = "a--"
		const val EM_DASH = "a—"
	}
}
