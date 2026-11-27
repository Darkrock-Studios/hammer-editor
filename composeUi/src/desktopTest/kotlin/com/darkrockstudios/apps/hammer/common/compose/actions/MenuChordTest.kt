package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.Keymap
import com.darkrockstudios.apps.hammer.common.components.menu.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.compose.TopAppBarDropdownMenu
import com.darkrockstudios.apps.hammer.scene_editor_menu_item_close
import com.darkrockstudios.apps.hammer.scene_editor_menu_item_rename
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MenuChordTest {

	private fun item(id: String, label: org.jetbrains.compose.resources.StringResource, actionId: Boolean) =
		MenuItemDescriptor(id, label, "", actionId = HammerActions.ItemClose.id.takeIf { actionId }) {}

	@Test
	fun `menu rows show their chord`() = runComposeUiTest {
		setContent {
			TopAppBarDropdownMenu(menuItems = setOf(item("close", Res.string.scene_editor_menu_item_close, actionId = true)))
		}

		onNodeWithContentDescription("More").performClick()

		onNodeWithText("Close").assertExists()
		onNodeWithText(KeyChord.mod(ChordKey.W).label()).assertExists()
	}

	@Test
	fun `rows without an action show none`() = runComposeUiTest {
		setContent {
			TopAppBarDropdownMenu(menuItems = setOf(item("rename", Res.string.scene_editor_menu_item_rename, actionId = false)))
		}

		onNodeWithContentDescription("More").performClick()

		onNodeWithText("Rename").assertExists()
		onNodeWithText(KeyChord.mod(ChordKey.W).label()).assertDoesNotExist()
	}

	@Test
	fun `the chord comes from the dispatcher's keymap`() = runComposeUiTest {
		val rebound = KeyChord.mod(ChordKey.E, shift = true)
		val keymap = Keymap(mapOf(HammerActions.ItemClose.id to listOf(rebound)))
		setContent {
			ProvideActions(ActionRegistry(keymap)) {
				TopAppBarDropdownMenu(menuItems = setOf(item("close", Res.string.scene_editor_menu_item_close, actionId = true)))
			}
		}

		onNodeWithContentDescription("More").performClick()

		onNodeWithText(rebound.label()).assertExists()
	}
}
