package com.darkrockstudios.apps.hammer.common.data

import dev.icerock.moko.resources.StringResource

data class MenuDescriptor(val id: String, val label: StringResource, val items: List<MenuItemDescriptor>)

data class MenuItemDescriptor(
	val id: String,
	val label: StringResource,
	val icon: String,
	val shortcut: KeyShortcut? = null,
	val action: (itemId: String) -> Unit,
)

data class KeyShortcut(
	/**
	 * Key that should be pressed to trigger an action
	 */
	val keyCode: Int,

	/**
	 * true if Ctrl modifier key should be pressed to trigger an action
	 */
	val ctrl: Boolean = false,

	/**
	 * true if Meta modifier key should be pressed to trigger an action
	 * (it is Command on macOs)
	 */
	val meta: Boolean = false,

	/**
	 * true if Alt modifier key should be pressed to trigger an action
	 */
	val alt: Boolean = false,

	/**
	 * true if Shift modifier key should be pressed to trigger an action
	 */
	val shift: Boolean = false,
)