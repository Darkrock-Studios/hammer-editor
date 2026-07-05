package com.darkrockstudios.apps.hammer.common.preview.protocolmismatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.components.protocolmismatch.ProtocolMismatch
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.protocolmismatch.ProtocolMismatchContent

private fun previewState(
	clientIsBehind: Boolean = true,
	latestVersionTag: String? = "v9.9.9",
	isNewVersionAvailable: Boolean = true,
	releaseUrl: String? = "https://github.com/Wavesonics/hammer-editor/releases/tag/v9.9.9",
) = ProtocolMismatch.State(
	clientProtocolVersion = 3,
	serverProtocolVersion = if (clientIsBehind) 5 else 2,
	clientIsBehind = clientIsBehind,
	currentVersion = "v3.5.3",
	latestVersionTag = latestVersionTag,
	isNewVersionAvailable = isNewVersionAvailable,
	releaseUrl = releaseUrl,
)

@Composable
private fun ProtocolMismatchPreviewScaffold(
	state: ProtocolMismatch.State,
	darkTheme: Boolean,
) {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, useDarkTheme = darkTheme) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
				contentAlignment = Alignment.Center,
			) {
				ProtocolMismatchContent(
					state = state,
					onOpenRelease = {},
					onDismiss = {},
				)
			}
		}
	}
}

@Preview(widthDp = 640, heightDp = 480)
@Composable
fun ProtocolMismatchClientBehindPreview() {
	ProtocolMismatchPreviewScaffold(previewState(), darkTheme = false)
}

@Preview(widthDp = 640, heightDp = 480)
@Composable
fun ProtocolMismatchClientBehindDarkPreview() {
	ProtocolMismatchPreviewScaffold(previewState(), darkTheme = true)
}

@Preview(widthDp = 640, heightDp = 480)
@Composable
fun ProtocolMismatchServerBehindPreview() {
	ProtocolMismatchPreviewScaffold(
		previewState(clientIsBehind = false, isNewVersionAvailable = false),
		darkTheme = false,
	)
}

@Preview(widthDp = 640, heightDp = 480)
@Composable
fun ProtocolMismatchNoVersionInfoPreview() {
	ProtocolMismatchPreviewScaffold(
		previewState(latestVersionTag = null, isNewVersionAvailable = false, releaseUrl = null),
		darkTheme = false,
	)
}
