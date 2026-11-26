package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class HdIndicationTest {

	private var ink = Color.Unspecified

	/** Just inside the control's own hairline border, where the frame draws. */
	private fun SemanticsNodeInteractionsProvider.leftEdge(tag: String): Color {
		val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
		return pixels[1, pixels.height / 2]
	}

	private fun androidx.compose.ui.test.ComposeUiTest.showButtons() = setContent {
		AppTheme(globalSettingsPreview, useDarkTheme = false) {
			ink = MaterialTheme.colorScheme.onSurface
			Column {
				HdHairlineButton(label = "Cancel", onClick = {}, modifier = Modifier.testTag("cancel"))
				HdHairlineButton(label = "Create", onClick = {}, emphasised = true, modifier = Modifier.testTag("create"))
			}
		}
	}

	@Test
	fun `Tab onto a button frames it in ink`() = runComposeUiTest {
		showButtons()
		waitForIdle()
		val before = leftEdge("create")

		onRoot().performKeyInput { pressKey(Key.Tab) }
		onRoot().performKeyInput { pressKey(Key.Tab) }
		waitForIdle()

		onNodeWithTag("create").assertIsFocused()
		assertNotEquals(ink, before)
		assertEquals(ink, leftEdge("create"))
	}

	@Test
	fun `a click shows no frame`() = runComposeUiTest {
		showButtons()
		onRoot().performKeyInput { pressKey(Key.Tab) }
		waitForIdle()

		onNodeWithTag("create").performMouseInput { click() }
		onNodeWithTag("cancel").performMouseInput { click() }
		waitForIdle()

		assertNotEquals(ink, leftEdge("cancel"))
		assertNotEquals(ink, leftEdge("create"))
	}
}
