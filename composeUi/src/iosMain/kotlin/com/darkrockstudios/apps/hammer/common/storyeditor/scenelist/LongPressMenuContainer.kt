package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

internal data class LongPressMenuEntry(
	val label: String,
	val icon: ImageVector,
	val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LongPressMenuContainer(
	entries: List<LongPressMenuEntry>,
	itemContent: @Composable (modifier: Modifier) -> Unit,
) {
	val hapticFeedback = LocalHapticFeedback.current
	var showMenu by remember { mutableStateOf(false) }

	Box {
		itemContent(
			Modifier.combinedClickable(
				onClick = {},
				onLongClick = {
					hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
					showMenu = true
				},
			)
		)

		DropdownMenu(
			expanded = showMenu,
			onDismissRequest = { showMenu = false }
		) {
			entries.forEach { entry ->
				DropdownMenuItem(
					text = { Text(entry.label) },
					onClick = {
						entry.onClick()
						showMenu = false
					},
					leadingIcon = {
						Icon(entry.icon, contentDescription = entry.label)
					},
				)
			}
		}
	}
}
