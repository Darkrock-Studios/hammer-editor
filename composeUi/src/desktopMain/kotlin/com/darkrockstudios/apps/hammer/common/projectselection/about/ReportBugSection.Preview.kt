package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

@Preview
@Composable
fun ReportBugSectionDarkPreview() = ReportBugSectionPreviewContent(isDark = true)

@Preview
@Composable
fun ReportBugSectionLightPreview() = ReportBugSectionPreviewContent(isDark = false)

@Composable
private fun ReportBugSectionPreviewContent(isDark: Boolean) {
	AppTheme(globalSettingsPreview, isDark) {
		Box(
			modifier = Modifier
				.background(MaterialTheme.colorScheme.surface)
				.padding(24.dp),
		) {
			ReportBugSection(section = 5, onReportBug = {})
		}
	}
}
