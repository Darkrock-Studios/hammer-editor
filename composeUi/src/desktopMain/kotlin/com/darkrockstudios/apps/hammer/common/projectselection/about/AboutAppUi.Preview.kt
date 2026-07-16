package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.util.CrashReport
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
		AboutAppUi(previewComponent)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenAboutAppUiTabletPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			AboutAppUi(previewComponent)
		}
	}
}

@Preview(widthDp = 900, heightDp = 1500)
@Composable
fun ScreenAboutAppUiTallPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			AboutAppUi(previewComponent)
		}
	}
}

private val previewComponent = object : AboutApp {
	override val state = MutableValue(
		AboutApp.State(
			latestVersion = "v9.9.9",
			currentVersion = "v1.0.0",
			newVersionAvailable = true,
			latestCrash = CrashReport(
				fileName = "crash-1700000000000.txt",
				content = "java.lang.IllegalStateException: preview crash",
			),
		)
	)

	override fun openDiscord() {}
	override fun openReddit() {}
	override fun openGithub() {}
	override fun reportBug() {}
	override fun viewReleaseDetails() {}
}