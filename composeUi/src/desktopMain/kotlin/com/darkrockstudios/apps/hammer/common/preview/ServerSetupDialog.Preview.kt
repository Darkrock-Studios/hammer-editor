package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent
import com.darkrockstudios.apps.hammer.common.projectselection.settings.ServerSetupDialogContent

@Preview(widthDp = 560, heightDp = 640)
@Composable
fun ServerSetupDialogPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				ServerSetupDialogContent(
					component = accountSettingsComponent(),
					scope = scope,
				)
			}
		}
	}
}
