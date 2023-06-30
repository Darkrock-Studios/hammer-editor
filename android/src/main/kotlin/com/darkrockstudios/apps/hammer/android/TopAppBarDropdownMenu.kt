package com.darkrockstudios.apps.hammer.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor

@Composable
fun TopAppBarDropdownMenu(
	menus: List<MenuDescriptor>
) {
	val expanded = remember { mutableStateOf(false) }

	Box(
		Modifier
			.wrapContentSize(Alignment.TopEnd)
	) {
		IconButton(onClick = {
			expanded.value = true
		}) {
			Icon(
				Icons.Filled.MoreVert,
				contentDescription = stringResource(R.string.overflow_menu_button),
				tint = MaterialTheme.colorScheme.onSurface
			)
		}
	}

	DropdownMenu(
		expanded = expanded.value,
		onDismissRequest = { expanded.value = false },
	) {
		menus.forEach { menu ->
			menu.items.forEach { item ->
				DropdownMenuItem(
					text = { Text(item.label.get()) },
					onClick = {
						expanded.value = false
						item.action(item.id)
					}
				)

				Divider()
			}
		}
	}
}