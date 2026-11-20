package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.projectselection.settings.FullCommandCard

/** The full command for an AppImage install, over a scrim. */
@Composable
internal fun FullCommandScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		FullCommandCard(
			command = "/home/you/Applications/Hammer-x86_64.AppImage",
			copied = false,
			onCopy = {},
			onClose = {},
		)
	}
}
