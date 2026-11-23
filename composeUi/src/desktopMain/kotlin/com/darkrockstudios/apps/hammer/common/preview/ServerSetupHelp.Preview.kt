package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.projectselection.settings.ServerSetupHelpContent

/** The About sync servers help over a scrim. */
@Preview(widthDp = 600, heightDp = 900)
@Composable
fun ServerSetupHelpPreview() {
	PreviewScrim {
		ServerSetupHelpContent(onClose = {})
	}
}
