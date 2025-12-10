package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.darkrockstudios.apps.hammer.common.projectselection.ServerSetupDialog
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent

@Preview
@Composable
private fun ServerSetupDialogPreview() {
	val scope = rememberCoroutineScope()

	ServerSetupDialog(
		accountSettingsComponent(),
		scope,
	)
}