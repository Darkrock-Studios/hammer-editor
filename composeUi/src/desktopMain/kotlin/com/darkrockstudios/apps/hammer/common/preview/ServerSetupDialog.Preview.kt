package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent
import com.darkrockstudios.apps.hammer.common.projectselection.settings.ServerSetupDialog

@Preview
@Composable
private fun ServerSetupDialogPreview() {
	val scope = rememberCoroutineScope()

	ServerSetupDialog(
		accountSettingsComponent(),
		scope,
	)
}