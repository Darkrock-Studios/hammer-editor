package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

@Preview
@Composable
fun ScreenAboutAppUiPreview() {
	AppTheme(globalSettingsPreview) {
		AboutAppUi(previewComponent(), onShowStudio = {})
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenAboutAppUiTabletPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			AboutAppUi(previewComponent(), onShowStudio = {})
		}
	}
}

@Preview
@Composable
fun ScreenAboutAppUiUpdateAvailablePreview() {
	AppTheme(globalSettingsPreview) {
		AboutAppUi(
			previewComponent(AppUpdateState.Available(version = "9.9.9", installable = true)),
			onShowStudio = {},
		)
	}
}

@Preview
@Composable
fun AppUpdateStatusStatesPreview() {
	val states = listOf(
		AppUpdateState.Idle,
		AppUpdateState.Checking,
		AppUpdateState.UpToDate,
		AppUpdateState.Available(version = "9.9.9", installable = true),
		AppUpdateState.Available(version = "9.9.9", installable = false),
		AppUpdateState.Downloading(version = "9.9.9", fraction = 0.42f),
		AppUpdateState.Installing(version = "9.9.9"),
		AppUpdateState.Failed(version = "9.9.9", reason = "HTTP 503"),
	)
	AppTheme(globalSettingsPreview) {
		Column(
			modifier = Modifier
				.background(MaterialTheme.colorScheme.surface)
				.padding(24.dp),
		) {
			for (state in states) {
				AppUpdateStatus(
					state = state,
					onCheckForUpdate = {},
					onUpdateApp = {},
					modifier = Modifier.padding(bottom = 24.dp),
				)
			}
		}
	}
}

private fun previewComponent(appUpdate: AppUpdateState = AppUpdateState.Unsupported) = object : AboutApp {
	override val state = MutableValue(
		AboutApp.State(currentVersion = "v1.0.0", appUpdate = appUpdate)
	)

	override fun openDiscord() {}
	override fun openReddit() {}
	override fun openGithub() {}
	override fun viewChangelog() {}
	override fun openLatestRelease() {}
	override fun checkForUpdate() {}
	override fun updateApp() {}
}
