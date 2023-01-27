package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState

fun getLocationIcon(location: ProjectSelection.Locations): ImageVector {
	return when (location) {
		ProjectSelection.Locations.Projects -> Icons.Filled.LibraryBooks
		ProjectSelection.Locations.Sittings -> Icons.Filled.Settings
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun ProjectSelectionUi(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()
	when (state.location) {
		ProjectSelection.Locations.Projects -> ProjectList(component, modifier)
		ProjectSelection.Locations.Sittings -> Settings(component, modifier)
	}
}