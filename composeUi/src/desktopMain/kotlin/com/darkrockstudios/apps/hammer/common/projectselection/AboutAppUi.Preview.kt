package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme

@Preview
@Composable
private fun AboutAppUiPreview() {
	AppTheme {
		AboutAppUi(previewComponent)
	}
}

private val previewComponent = object : AboutApp {
	override fun openDiscord() {}
	override fun openReddit() {}
	override fun openGithub() {}
}