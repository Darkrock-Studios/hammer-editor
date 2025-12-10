package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.more_menu_button

@Composable
fun TopAppBarDropdownMenu(
	modifier: Modifier = Modifier,
	menuItems: Set<MenuItemDescriptor>
) {
	var expanded by rememberSaveable { mutableStateOf(false) }

	Box(modifier = modifier) {
		IconButton(onClick = {
			expanded = true
		}) {
			Icon(
				Icons.Filled.MoreVert,
				contentDescription = Res.string.more_menu_button.get(),
				tint = MaterialTheme.colorScheme.onSurface
			)
		}

		DropdownMenu(
			modifier = Modifier.align(TopEnd),
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			menuItems.forEach { item ->
				DropdownMenuItem(
					text = { Text(item.label.get()) },
					onClick = {
						expanded = false
						item.action(item.id)
					}
				)
			}
		}
	}
}