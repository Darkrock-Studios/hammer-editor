package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent
import com.darkrockstudios.apps.hammer.common.projectselection.defaultAccountSettingsComponentState
import com.darkrockstudios.apps.hammer.common.projectselection.settings.AccountSettingsUi
import com.darkrockstudios.apps.hammer.common.projectselection.settings.SettingsTab
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings

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

@Preview
@Composable
internal fun ScreenAccountSettingsUiOledBlackPreview() {
	val component = accountSettingsComponent(
		defaultAccountSettingsComponentState.copy(serverSetup = false, uiTheme = UiTheme.OledBlack)
	)
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview.copy(uiTheme = UiTheme.OledBlack), useDarkTheme = true) {
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
			serverIsConfigured = true,
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
			serverIsConfigured = true,
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

private val connectedState get() = defaultAccountSettingsComponentState.copy(
	serverSetup = false,
	serverIsConfigured = true,
	serverIsLoggedIn = true,
	currentEmail = "admin@example.com",
	currentUrl = "https://hammer-server.com",
	serverWorking = false,
)

@Composable
private fun TabletSettings(state: AccountSettings.State, tab: SettingsTab) {
	val component = accountSettingsComponent(state)
	val rootSnackbar = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		TabletPreviewSurface {
			AccountSettingsUi(component, rootSnackbar, initialTab = tab)
		}
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
internal fun ScreenAccountSettingsUiWritingTabletPreview() = TabletSettings(connectedState, SettingsTab.Writing)

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
internal fun ScreenAccountSettingsUiSyncTabletPreview() = TabletSettings(connectedState, SettingsTab.Sync)

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
internal fun ScreenAccountSettingsUiSyncNotSetUpTabletPreview() =
	TabletSettings(defaultAccountSettingsComponentState.copy(serverSetup = false), SettingsTab.Sync)
