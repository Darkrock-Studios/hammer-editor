package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val DialogMaxWidth = 520.dp

@Composable
fun ExportOptionsDialog(
	visible: Boolean,
	initialOptions: ExportOptions,
	onCancel: () -> Unit,
	onConfirm: (ExportOptions) -> Unit,
) {
	var treatAsChapters by remember(initialOptions, visible) {
		mutableStateOf(initialOptions.treatTopLevelAsChapters)
	}
	var format by remember(initialOptions, visible) {
		mutableStateOf(initialOptions.format)
	}

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onCancel,
		dismissOnTapOutside = true,
	) {
		Surface(
			modifier = Modifier
				.padding(Ui.Padding.M)
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
			Column {
				HdMasthead(
					section = stringResource(Res.string.project_home_export_section),
					trailing = {
						HdMastheadAction(
							label = stringResource(Res.string.project_home_export_close),
							onClick = onCancel,
						)
					},
				)
				HdFolioDivider()

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							start = Ui.Padding.XL,
							end = Ui.Padding.XL,
							top = Ui.Padding.L,
							bottom = Ui.Padding.M,
						),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = Res.string.project_home_export_dialog_title.get(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurface,
					)
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				Column(
					modifier = Modifier.padding(
						horizontal = Ui.Padding.XL,
						vertical = Ui.Padding.XL,
					),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
				) {
					HdHairlineToggleRow(
						checked = treatAsChapters,
						onCheckedChange = { treatAsChapters = it },
						label = stringResource(Res.string.project_home_export_chapters_label),
					)

					HdHairlineSegmentedPicker(
						title = stringResource(Res.string.project_home_export_format_label),
						options = AVAILABLE_EXPORT_FORMATS,
						selected = format,
						onSelect = { format = it },
						label = { stringResource(it.labelRes()) },
					)
				}

				Spacer(modifier = Modifier.height(Ui.Padding.M))

				HdButtonBar(
					cancelLabel = stringResource(Res.string.project_home_export_cancel),
					primaryLabel = stringResource(Res.string.project_home_export_execute),
					onCancel = onCancel,
					onPrimary = {
						onConfirm(
							ExportOptions(
								treatTopLevelAsChapters = treatAsChapters,
								format = format,
							)
						)
					},
				)
			}
		}
	}
}

private val AVAILABLE_EXPORT_FORMATS = listOf(ExportFormat.Markdown, ExportFormat.Epub)

private fun ExportFormat.labelRes(): StringResource = when (this) {
	ExportFormat.Markdown -> Res.string.project_home_export_format_markdown
	ExportFormat.Epub -> Res.string.project_home_export_format_epub
}
