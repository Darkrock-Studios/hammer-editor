package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
	HdHairlineDialogShell(
		title = Res.string.project_home_export_help_title.get(),
		onClose = onDismiss,
		closeContentDescription = Res.string.project_home_export_help_close.get(),
		modifier = Modifier.widthIn(max = DialogMaxWidth),
	) {
		Column(
			modifier = Modifier
				.weight(1f, fill = false)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			ExportFormatHelpRows.forEachIndexed { index, format ->
				if (index > 0) {
					HorizontalDivider(
						thickness = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
					)
				}
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
}
