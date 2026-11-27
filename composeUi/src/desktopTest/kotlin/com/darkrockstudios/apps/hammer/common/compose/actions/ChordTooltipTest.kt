package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.Keymap
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownFormatBar
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.state.rememberTextEditorState
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ChordTooltipTest {

	@Test
	fun `a tooltip names its action's chord`() = runComposeUiTest {
		setContent { ChordTooltipText("Bold", HammerActions.EditBold.id) }

		onNodeWithText("Bold").assertExists()
		onNodeWithText(KeyChord.mod(ChordKey.B).label()).assertExists()
	}

	@Test
	fun `an action without a chord shows just its label`() = runComposeUiTest {
		setContent {
			ProvideActions(ActionRegistry(Keymap(emptyMap()))) {
				ChordTooltipText("Bold", HammerActions.EditBold.id)
			}
		}

		onNodeWithText("Bold").assertExists()
		onNodeWithText(KeyChord.mod(ChordKey.B).label()).assertDoesNotExist()
	}

	@Test
	fun `the format bar's insert image tooltip shows the keymap chord`() = runComposeUiTest {
		setContent {
			KoinApplicationPreview {
				val state = rememberTextEditorState()
				val markdown = remember(state) { state.withMarkdown(HammerMarkdownConfiguration) }
				MarkdownFormatBar(markdownState = markdown, onInsertImage = {})
			}
		}

		onNodeWithTag(MARKDOWN_FORMAT_BAR_INSERT_IMAGE_TAG, useUnmergedTree = true).performMouseInput { enter(center) }
		waitForIdle()

		onNodeWithText("Insert image", useUnmergedTree = true).assertExists()
		onNodeWithText(KeyChord.mod(ChordKey.I, shift = true).label(), useUnmergedTree = true).assertExists()
	}
}
