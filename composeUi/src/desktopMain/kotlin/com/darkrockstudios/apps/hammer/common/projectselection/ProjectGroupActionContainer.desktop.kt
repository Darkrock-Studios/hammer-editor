package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
actual fun ProjectGroupActionContainer(
	items: List<ProjectGroupMenuItem>,
	content: @Composable () -> Unit,
) {
	val entries = items.map { it.label.get() to it.onClick }
	ContextMenuArea(
		items = { entries.map { (label, onClick) -> ContextMenuItem(label = label, onClick = onClick) } },
		content = content,
	)
}
