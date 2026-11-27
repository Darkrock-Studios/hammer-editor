package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.darkrockstudios.texteditor.contextmenu.TextEditorContextMenuState

/** A text editor's context menu state whose menu, spell check suggestions included, is a [MenuOverlay] while open. */
@Composable
fun rememberOverlayContextMenuState(): TextEditorContextMenuState {
	val menu = remember { TextEditorContextMenuState() }
	MenuOverlay(menu.isVisible, menu::dismissMenu)
	return menu
}
