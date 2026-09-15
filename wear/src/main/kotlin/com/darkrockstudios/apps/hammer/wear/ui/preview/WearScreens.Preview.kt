package com.darkrockstudios.apps.hammer.wear.ui.preview

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.wear.components.projects.WearProjects
import com.darkrockstudios.apps.hammer.wear.components.signin.ManualSignIn
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState
import com.darkrockstudios.apps.hammer.wear.ui.ManualSignInContent
import com.darkrockstudios.apps.hammer.wear.ui.OnboardingScreen
import com.darkrockstudios.apps.hammer.wear.ui.PairingScreen
import com.darkrockstudios.apps.hammer.wear.ui.WearProjectsScreen
import com.darkrockstudios.apps.hammer.wear.ui.theme.HammerWearTheme

@Composable
private fun WearPreviewFrame(content: @Composable () -> Unit) {
	HammerWearTheme {
		AppScaffold { content() }
	}
}

@WearPreviewDevices
@Composable
private fun OnboardingPreview() {
	WearPreviewFrame { OnboardingScreen(onPair = {}, onManualSignIn = {}) }
}

@WearPreviewDevices
@Composable
private fun PairingAwaitingPreview() {
	WearPreviewFrame {
		PairingScreen(state = PairingState.AwaitingConfirmation, onRetry = {}, onCancel = {})
	}
}

@WearPreviewDevices
@Composable
private fun PairingDeclinedPreview() {
	WearPreviewFrame {
		PairingScreen(state = PairingState.Failed(PairErrorCode.Declined), onRetry = {}, onCancel = {})
	}
}

@WearPreviewDevices
@Composable
private fun PairingPhoneNotFoundPreview() {
	WearPreviewFrame {
		PairingScreen(state = PairingState.PhoneNotFound, onRetry = {}, onCancel = {})
	}
}

@WearPreviewDevices
@Composable
private fun ManualSignInEmptyPreview() {
	WearPreviewFrame {
		ManualSignInContent(
			state = ManualSignIn.State(),
			onEditServer = {},
			onEditEmail = {},
			onEditPassword = {},
			onSignIn = {},
		)
	}
}

@WearPreviewDevices
@Composable
private fun ManualSignInErrorPreview() {
	WearPreviewFrame {
		ManualSignInContent(
			state = ManualSignIn.State(
				server = "hammer.ink",
				email = "writer@example.com",
				hasPassword = true,
				error = ManualSignIn.SignInError.Message("Invalid credentials"),
			),
			onEditServer = {},
			onEditEmail = {},
			onEditPassword = {},
			onSignIn = {},
		)
	}
}

@WearPreviewDevices
@Composable
private fun ManualSignInBusyPreview() {
	WearPreviewFrame {
		ManualSignInContent(
			state = ManualSignIn.State(server = "hammer.ink", email = "writer@example.com", hasPassword = true, busy = true),
			onEditServer = {},
			onEditEmail = {},
			onEditPassword = {},
			onSignIn = {},
		)
	}
}

@WearPreviewDevices
@Composable
private fun ProjectsPlaceholderPreview() {
	WearPreviewFrame {
		WearProjectsScreen(state = WearProjects.State(accountEmail = "writer@example.com"), onSignOut = {})
	}
}
