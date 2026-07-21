package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent
import com.darkrockstudios.apps.hammer.common.projectselection.defaultAccountSettingsComponentState
import com.darkrockstudios.apps.hammer.common.projectselection.settings.AccountSettingsUi

@Preview
@Composable
internal fun ScreenAccountSettingsUiPreview() {
	val component = accountSettingsComponent(
		defaultAccountSettingsComponentState.copy(serverSetup = false)
	)
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			AccountSettingsUi(component, rootSnackbar)
		}
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
internal fun ScreenAccountSettingsUiTabletPreview() {
	val component = accountSettingsComponent(
		defaultAccountSettingsComponentState.copy(
			serverSetup = false,
			serverIsLoggedIn = true,
			currentEmail = "admin@example.com",
			currentUrl = "https://hammer-server.com",
			serverWorking = false,
		)
	)
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		TabletPreviewSurface {
			AccountSettingsUi(component, rootSnackbar)
		}
	}
}

@Preview
@Composable
internal fun ScreenAccountSettingsUiServerConfiguredPreview() {
	val component = accountSettingsComponent(
		defaultAccountSettingsComponentState.copy(
			serverSetup = false,
			serverIsLoggedIn = true,
			currentEmail = "admin@example.com",
			currentUrl = "https://hammer-server.com",
			serverWorking = false,
		)
	)
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			AccountSettingsUi(component, rootSnackbar)
		}
	}
}