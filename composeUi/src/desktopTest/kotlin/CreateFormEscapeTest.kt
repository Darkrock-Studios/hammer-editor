import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.compose.actions.EscapeRouter
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import com.darkrockstudios.apps.hammer.common.compose.focus.escapeTwiceToLeave
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private const val BODY = "create-body"
private const val BUTTON = "create-button"

/** A new-item form's body editor and the button after it, with Esc wired as the create screens wire it. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class CreateFormEscapeTest {

	private var backs = 0
	private val registry = ActionRegistry()
	private val router = EscapeRouter(registry) {
		backs++
		true
	}

	private fun ComposeUiTest.showForm() {
		setContent {
			KoinApplicationPreview {
				ProvideActions(registry) {
					Box(Modifier.onPreviewKeyEvent(router::onPreviewKeyEvent).onKeyEvent(router::onKeyEvent)) {
						Column(Modifier.escapeTwiceToLeave()) {
							MarkdownEditField(
								initialMarkdown = "",
								onMarkdownChanged = {},
								enableSpellChecking = false,
								autoFocus = true,
								testTag = BODY,
							)
							Box(Modifier.size(40.dp).testTag(BUTTON).focusable())
						}
					}
				}
			}
		}
		waitForIdle()
	}

	/** Sent through the form's window; it reaches whatever is focused there. */
	private fun ComposeUiTest.tap(key: Key) {
		onNodeWithTag(BUTTON).performKeyPress(KeyEvent(key = key, type = KeyEventType.KeyDown))
		waitForIdle()
		onNodeWithTag(BUTTON).performKeyPress(KeyEvent(key = key, type = KeyEventType.KeyUp))
		waitForIdle()
	}

	@Test
	fun `Tab in the body stays in the body`() = runComposeUiTest {
		showForm()

		tap(Key.Tab)

		onNodeWithTag(BUTTON).assertIsNotFocused()
	}

	@Test
	fun `Esc then Tab moves from the body to the next control`() = runComposeUiTest {
		showForm()

		tap(Key.Escape)
		tap(Key.Tab)

		onNodeWithTag(BUTTON).assertIsFocused()
		assertEquals(0, backs)
	}

	@Test
	fun `a second Esc in a row goes back`() = runComposeUiTest {
		showForm()

		tap(Key.Escape)
		val backsAfterFirst = backs
		tap(Key.Escape)

		assertEquals(0, backsAfterFirst)
		assertEquals(1, backs)
	}

	@Test
	fun `a key between the two starts over`() = runComposeUiTest {
		showForm()

		tap(Key.Escape)
		tap(Key.A)
		tap(Key.Escape)

		assertEquals(0, backs)
	}

	@Test
	fun `a click between the two starts over`() = runComposeUiTest {
		showForm()

		tap(Key.Escape)
		onNodeWithTag(BUTTON).performMouseInput { click() }
		waitForIdle()
		tap(Key.Escape)

		assertEquals(0, backs)
	}
}
