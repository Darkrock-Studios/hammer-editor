package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.help.HelpAllTopicsContent
import com.darkrockstudios.apps.hammer.common.help.HelpDialogContent
import com.darkrockstudios.apps.hammer.common.help.HelpScreen

/** The Scenes screen's help dialog over a scrim. */
@Preview(widthDp = 600, heightDp = 900)
@Composable
fun HelpDialogScenesPreview() {
	PreviewScrim {
		HelpDialogContent(screen = HelpScreen.Scenes, onClose = {})
	}
}

/** Every help topic, as the dialog shows them from About or after "All help topics". */
@Preview(widthDp = 600, heightDp = 900)
@Composable
fun HelpDialogAllTopicsPreview() {
	PreviewScrim {
		HelpAllTopicsContent(onClose = {})
	}
}
