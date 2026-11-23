package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.projectselection.settings.FullCommandCard

/** The full command for an AppImage install, over a scrim. */
@Composable
internal fun FullCommandScrimPreview() {
	PreviewScrim {
		FullCommandCard(
			command = "/home/you/Applications/Hammer-x86_64.AppImage",
			copied = false,
			onCopy = {},
			onClose = {},
		)
	}
}
