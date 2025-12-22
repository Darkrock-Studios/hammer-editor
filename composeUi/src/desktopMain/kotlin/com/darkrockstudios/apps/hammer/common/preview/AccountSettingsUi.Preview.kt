package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent
import com.darkrockstudios.apps.hammer.common.projectselection.defaultAccountSettingsComponentState
import com.darkrockstudios.apps.hammer.common.projectselection.settings.AccountSettingsUi

@Preview
@Composable
internal fun AccountSettingsUiPreview() {
	val component = accountSettingsComponent()
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			AccountSettingsUi(component, rootSnackbar)
		}
	}
}

@Preview
@Composable
internal fun AccountSettingsUiNoServerSetupPreview() {
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

@Preview
@Composable
internal fun AccountSettingsUiServerConfiguredPreview() {
	val component = accountSettingsComponent(
		defaultAccountSettingsComponentState.copy(
			serverSetup = false,
			serverIsLoggedIn = true,
			currentEmail = "admin@example.com",
			currentUrl = "https://hammer-server.com",
			currentSsl = true,
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