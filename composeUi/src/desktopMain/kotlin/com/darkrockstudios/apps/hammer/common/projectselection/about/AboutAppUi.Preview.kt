package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

@Preview
@Composable
private fun AboutAppUiPreview() {
	AppTheme(globalSettingsPreview) {
		AboutAppUi(previewComponent)
	}
}

private val previewComponent = object : AboutApp {
	override val state = MutableValue(
		AboutApp.State(
			latestVersion = "v9.9.9",
			currentVersion = "v1.0.0",
			newVersionAvailable = true
		)
	)

	override fun openDiscord() {}
	override fun openReddit() {}
	override fun openGithub() {}
	override fun openLogDirectory() {}
}