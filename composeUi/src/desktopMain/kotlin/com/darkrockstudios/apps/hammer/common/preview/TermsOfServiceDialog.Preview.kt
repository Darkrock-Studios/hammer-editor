package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.settings.TermsOfServiceDialogContent

private const val SAMPLE_TOS =
	"By creating an account on this server you agree to use it responsibly. " +
		"Do not upload unlawful content. The server is provided as-is with no warranty, " +
		"and the administrator may remove accounts that abuse the service.\n\n" +
		"Your projects are stored so they can be synchronized across your devices. " +
		"You remain the owner of everything you write."

@Preview(widthDp = 560, heightDp = 640)
@Composable
fun TermsOfServiceDialogPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				TermsOfServiceDialogContent(
					text = SAMPLE_TOS,
					working = false,
					onAccept = {},
					onDecline = {},
				)
			}
		}
	}
}
