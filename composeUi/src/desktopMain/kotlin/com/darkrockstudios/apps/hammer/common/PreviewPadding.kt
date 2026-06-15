package com.darkrockstudios.apps.hammer.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

/**
 * Preview wrapper that applies the app theme and surface background so a
 * component renders against the same backdrop it has in the running app,
 * rather than the renderer's transparent default.
 */
@Composable
fun Padded(composable: @Composable () -> Unit) {
	AppTheme(globalSettingsPreview) {
		Box(
			modifier = Modifier
				.background(MaterialTheme.colorScheme.background)
				.padding(32.dp),
		) {
			composable()
		}
	}
}
