package com.darkrockstudios.apps.hammer.android.wear.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.android.wear.WearPairingDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings

private val previewSettings = GlobalSettings(projectsDirectory = "/projects")

@Preview
@Composable
private fun WearPairingDialogSignedInPreview() {
	AppTheme(settings = previewSettings, useDarkTheme = false) {
		WearPairingDialog(
			deviceLabel = "Pixel Watch 3",
			accountEmail = "writer@example.com",
			onApprove = {},
			onDecline = {},
			modifier = Modifier.padding(Ui.Padding.M),
		)
	}
}

@Preview
@Composable
private fun WearPairingDialogNotSignedInDarkPreview() {
	AppTheme(settings = previewSettings, useDarkTheme = true) {
		WearPairingDialog(
			deviceLabel = "Pixel Watch 3",
			accountEmail = null,
			onApprove = {},
			onDecline = {},
			modifier = Modifier.padding(Ui.Padding.M),
		)
	}
}
