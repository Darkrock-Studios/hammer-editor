package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNoticeStrip
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.projectselection.AppUpdateBanner

private val bannerStates = listOf(
	AppUpdateState.Available(version = "9.9.9", installable = true),
	AppUpdateState.Available(version = "9.9.9", installable = false),
	AppUpdateState.Downloading(version = "9.9.9", fraction = 0.42f),
	AppUpdateState.Installing(version = "9.9.9"),
	AppUpdateState.Failed(version = "9.9.9", reason = "HTTP 503"),
)

@Preview
@Composable
fun AppUpdateBannerStatesPreview() {
	AppTheme(globalSettingsPreview, false) {
		Column(modifier = Modifier.width(720.dp).background(MaterialTheme.colorScheme.background)) {
			for (state in bannerStates) {
				AppUpdateBanner(state = state, onUpdate = {}, onOpenRelease = {}, onDismiss = {})
				Spacer(Modifier.height(16.dp))
			}
		}
	}
}

@Preview
@Composable
fun AppUpdateBannerStatesDarkPreview() {
	AppTheme(globalSettingsPreview, true) {
		Column(modifier = Modifier.width(720.dp).background(MaterialTheme.colorScheme.background)) {
			for (state in bannerStates) {
				AppUpdateBanner(state = state, onUpdate = {}, onOpenRelease = {}, onDismiss = {})
				Spacer(Modifier.height(16.dp))
			}
		}
	}
}

@Preview
@Composable
fun HdNoticeStripPreview() {
	AppTheme(globalSettingsPreview, false) {
		Column(modifier = Modifier.width(720.dp).background(MaterialTheme.colorScheme.background)) {
			HdNoticeStrip(
				title = "A title with two actions",
				detail = "And a detail line underneath it.",
				primaryLabel = "Primary",
				secondaryLabel = "Secondary",
			)
			Spacer(Modifier.height(16.dp))
			HdNoticeStrip(
				title = "Progress, no actions",
				progress = 0.6f,
			)
			Spacer(Modifier.height(16.dp))
			HdNoticeStrip(title = "Title only")
		}
	}
}
