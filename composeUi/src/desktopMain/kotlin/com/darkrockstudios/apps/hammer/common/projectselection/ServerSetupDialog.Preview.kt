package com.darkrockstudios.apps.hammer.common.projectselection.serversetupdialog

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.darkrockstudios.apps.hammer.common.projectselection.ServerSetupDialog
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent

@Preview
@Composable
private fun ServerSetupDialogPreview() {
	val scope = rememberCoroutineScope()
	val snackbar = remember { SnackbarHostState() }

	ServerSetupDialog(
		accountSettingsComponent(),
		scope,
	)
}