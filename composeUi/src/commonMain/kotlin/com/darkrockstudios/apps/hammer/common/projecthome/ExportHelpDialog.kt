package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import org.jetbrains.compose.resources.StringResource

private val DialogMaxWidth = 480.dp

private class ExportFormatHelp(val label: StringResource, val description: StringResource)

private val ExportFormatHelpRows = listOf(
	ExportFormatHelp(
		Res.string.project_home_export_format_epub,
		Res.string.project_home_export_help_format_epub,
	),
	ExportFormatHelp(
		Res.string.project_home_export_format_docx,
		Res.string.project_home_export_help_format_docx,
	),
	ExportFormatHelp(
		Res.string.project_home_export_format_rtf,
		Res.string.project_home_export_help_format_rtf,
	),
	ExportFormatHelp(
		Res.string.project_home_export_format_pdf,
		Res.string.project_home_export_help_format_pdf,
	),
	ExportFormatHelp(
		Res.string.project_home_export_format_markdown,
		Res.string.project_home_export_help_format_markdown,
	),
)

/** Animated wrapper around [ExportHelpContent]. Preview [ExportHelpContent] directly for a static frame. */
@Composable
internal fun ExportHelpDialog(onDismiss: () -> Unit) {
	var isOpen by remember { mutableStateOf(true) }

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = { isOpen = false },
		onClosed = onDismiss,
		properties = DialogProperties(
			dismissOnBackPress = true,
			dismissOnClickOutside = true,
			usePlatformDefaultWidth = false,
		),
	) {
		Box(modifier = Modifier.predictiveBackTransform()) {
			ExportHelpContent(onDismiss = { isOpen = false })
		}
	}
}

@Composable
internal fun ExportHelpContent(onDismiss: () -> Unit) {
	Surface(
		modifier = Modifier
			.padding(Ui.Padding.XL)
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		),
	) {
		Column(modifier = Modifier.fillMaxWidth()) {
			HdMasthead(
				section = "HELP · EXPORT",
				trailing = { HdMastheadAction(label = "× CLOSE", onClick = onDismiss) },
			)
			HdFolioDivider()

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				Text(
					text = Res.string.project_home_export_help_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
				)
				Text(
					text = Res.string.project_home_export_help_intro.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
					ExportFormatHelpRows.forEachIndexed { index, format ->
						if (index > 0) {
							HorizontalDivider(
								thickness = Dp.Hairline,
								color = MaterialTheme.colorScheme.outlineVariant,
							)
						}
						Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
							Text(
								text = format.label.get(),
								style = MaterialTheme.typography.titleSmall,
								color = MaterialTheme.colorScheme.onSurface,
							)
							Text(
								text = format.description.get(),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}
			}

			HdFolioDivider()
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalArrangement = Arrangement.End,
			) {
				HdHairlineButton(
					label = Res.string.project_home_export_help_dismiss.get(),
					onClick = onDismiss,
					emphasised = true,
				)
			}
		}
	}
}
