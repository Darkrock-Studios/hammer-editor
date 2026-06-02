package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor

@Composable
fun DetailViewDropdownMenu(menuItems: Set<MenuItemDescriptor>) {
	TopAppBarDropdownMenu(menuItems = menuItems)
}
