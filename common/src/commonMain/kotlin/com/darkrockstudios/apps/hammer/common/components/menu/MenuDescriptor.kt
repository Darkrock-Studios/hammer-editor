package com.darkrockstudios.apps.hammer.common.components.menu

import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import org.jetbrains.compose.resources.StringResource

data class MenuDescriptor(val id: String, val label: StringResource, val items: List<MenuItemDescriptor>)

data class MenuItemDescriptor(
	val id: String,
	val label: StringResource,
	val icon: String,
	val actionId: ActionId? = null,
	val action: (itemId: String) -> Unit,
)
