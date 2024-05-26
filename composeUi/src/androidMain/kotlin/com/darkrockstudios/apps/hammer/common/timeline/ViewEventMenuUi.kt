package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.TopAppBarDropdownMenu

@Composable
actual fun ViewEventMenuUi(component: ViewTimeLineEvent) {
	val state by component.state.subscribeAsState()

	TopAppBarDropdownMenu(menuItems = state.menuItems)
}