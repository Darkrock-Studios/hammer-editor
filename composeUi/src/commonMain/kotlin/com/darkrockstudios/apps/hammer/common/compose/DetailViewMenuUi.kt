package com.darkrockstudios.apps.hammer.common.compose

import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginActionMenuItems
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor

/** [menuItems], then the plugin actions for [place], run on the item with [itemId], when given. */
@Composable
fun DetailViewDropdownMenu(menuItems: Set<MenuItemDescriptor>, place: ActionPlace? = null, itemId: Int? = null) {
	TopAppBarDropdownMenu(
		menuItems = menuItems,
		extraItems = { close -> if (place != null) PluginActionMenuItems(place, itemId, onChosen = close) },
	)
}
