package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

@Preview
@Composable
fun ScreenAboutAppUiPreview() {
	AppTheme(globalSettingsPreview) {
		AboutAppUi(previewComponent, onShowStudio = {})
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenAboutAppUiTabletPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			AboutAppUi(previewComponent, onShowStudio = {})
		}
	}
}

private val previewComponent = object : AboutApp {
	override val state = MutableValue(
		AboutApp.State(currentVersion = "v1.0.0")
	)

	override fun openDiscord() {}
	override fun openReddit() {}
	override fun openGithub() {}
	override fun viewChangelog() {}
	override fun openLatestRelease() {}
}