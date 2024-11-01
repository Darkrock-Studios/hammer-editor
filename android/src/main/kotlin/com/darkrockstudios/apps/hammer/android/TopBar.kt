package com.darkrockstudios.apps.hammer.android

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
		showBack -> Icons.AutoMirrored.Filled.ArrowBack
		else -> when (drawerOpen.currentValue) {
			DrawerValue.Closed -> Icons.Filled.Menu
			DrawerValue.Open -> Icons.AutoMirrored.Filled.MenuOpen
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
					contentDescription = stringResource(R.string.navdrawer_button),
				)
			}
		},
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
				Ui.Elevation.MEDIUM
			)
		),
		actions = actions
	)
}