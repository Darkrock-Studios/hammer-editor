package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.texteditor.contextmenu.TextEditorContextMenuState
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EditorMenuOverlayTest {

	@Test
	fun `an open editor menu is the overlay Esc closes`() = runComposeUiTest {
		val registry = ActionRegistry()
		lateinit var menu: TextEditorContextMenuState
		setContent {
			ProvideActions(registry) {
				menu = rememberOverlayContextMenuState()
			}
		}
		waitForIdle()
		assertFalse(registry.hasOverlay())

		runOnIdle { menu.showMenu(Offset.Zero) }
		waitForIdle()
		assertTrue(registry.hasOverlay())

		assertTrue(registry.dismissTopOverlay())
		waitForIdle()
		assertFalse(menu.isVisible)
		assertFalse(registry.hasOverlay())
	}
}
