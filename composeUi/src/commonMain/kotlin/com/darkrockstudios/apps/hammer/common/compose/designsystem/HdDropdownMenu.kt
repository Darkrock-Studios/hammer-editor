package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.darkrockstudios.apps.hammer.common.compose.actions.MenuOverlay

/** A Material [DropdownMenu] that is the topmost overlay while open ([MenuOverlay]), so Esc closes only the menu. */
@Composable
fun HdDropdownMenu(
	expanded: Boolean,
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
	offset: DpOffset = DpOffset(0.dp, 0.dp),
	properties: PopupProperties = PopupProperties(focusable = true),
	content: @Composable ColumnScope.() -> Unit,
) {
	MenuOverlay(expanded, onDismissRequest)
	DropdownMenu(
		expanded = expanded,
		onDismissRequest = onDismissRequest,
		modifier = modifier,
		offset = offset,
		properties = properties,
		content = content,
	)
}
