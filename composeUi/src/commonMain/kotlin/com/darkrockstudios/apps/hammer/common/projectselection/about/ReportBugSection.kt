package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_report_bug_button
import com.darkrockstudios.apps.hammer.about_report_bug_description
import com.darkrockstudios.apps.hammer.about_report_bug_header
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.resources.get

/** Sends the user to the GitHub bug report form, pointing them at the log actions that follow it. */
@Composable
fun ReportBugSection(
	section: Int,
	onReportBug: () -> Unit,
	modifier: Modifier = Modifier,
) {
	HdHairlineSection(
		section = section,
		title = Res.string.about_report_bug_header.get(),
		modifier = modifier,
		contentSpacing = 14.dp,
	) {
		Text(
			text = Res.string.about_report_bug_description.get(),
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HdHairlineButton(
			label = Res.string.about_report_bug_button.get(),
			onClick = onReportBug,
			emphasised = true,
		)
	}
}
