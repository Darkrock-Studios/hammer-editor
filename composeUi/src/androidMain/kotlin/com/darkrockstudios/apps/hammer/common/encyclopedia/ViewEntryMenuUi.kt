package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.compose.TopAppBarDropdownMenu

@Composable
actual fun ViewEntryMenuUi(component: ViewEntry) {
	val state by component.state.subscribeAsState()

	TopAppBarDropdownMenu(menuItems = state.menuItems)
}