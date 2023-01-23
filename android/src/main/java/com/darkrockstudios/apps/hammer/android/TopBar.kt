package com.darkrockstudios.apps.hammer.android

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.compose.Ui

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopBar(
	title: String = "",
	drawerOpen: DrawerState,
	showBack: Boolean = false,
	onButtonClicked: () -> Unit,
	actions: @Composable (RowScope.() -> Unit) = {},
) {
	val icon = when {
		showBack -> Icons.Filled.ArrowBack
		else -> when (drawerOpen.currentValue) {
			DrawerValue.Closed -> Icons.Filled.Menu
			DrawerValue.Open -> Icons.Filled.MenuOpen
		}
	}

	TopAppBar(
		title = {
			Text(
				text = title
			)
		},
		navigationIcon = {
			IconButton(onClick = { onButtonClicked() }) {
				Icon(
					icon,
					contentDescription = "Nav Drawer",
				)
			}
		},
		colors = TopAppBarDefaults.smallTopAppBarColors(
			containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
				Ui.Elevation.MEDIUM
			)
		),
		actions = actions
	)
}