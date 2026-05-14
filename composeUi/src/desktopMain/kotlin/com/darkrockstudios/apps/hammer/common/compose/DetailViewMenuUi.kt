package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor

@Composable
actual fun DetailViewDropdownMenu(menuItems: Set<MenuItemDescriptor>) {
	TopAppBarDropdownMenu(menuItems = menuItems)
}
