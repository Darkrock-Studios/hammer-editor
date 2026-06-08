package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdButtonBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.project_home_export_cancel
import com.darkrockstudios.apps.hammer.project_home_export_chapters_label
import com.darkrockstudios.apps.hammer.project_home_export_close
import com.darkrockstudios.apps.hammer.project_home_export_dialog_title
import com.darkrockstudios.apps.hammer.project_home_export_execute
import com.darkrockstudios.apps.hammer.project_home_export_format_epub
import com.darkrockstudios.apps.hammer.project_home_export_format_label
import com.darkrockstudios.apps.hammer.project_home_export_format_markdown
import com.darkrockstudios.apps.hammer.project_home_export_format_pdf
import com.darkrockstudios.apps.hammer.project_home_export_section
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val DialogMaxWidth = 520.dp

@Composable
fun ExportOptionsDialog(
	visible: Boolean,
	initialOptions: ExportOptions,
	onCancel: () -> Unit,
	onConfirm: (ExportOptions) -> Unit,
	working: Boolean = false,
) {
	var treatAsChapters by remember(initialOptions, visible) {
		mutableStateOf(initialOptions.treatTopLevelAsChapters)
	}
	var format by remember(initialOptions, visible) {
		mutableStateOf(initialOptions.format)
	}

	AnimatedDialog(
		visible = visible,
		onCloseRequest = { if (!working) onCancel() },
		dismissOnTapOutside = !working,
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
							onClick = { if (!working) onCancel() },
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

					HdHairlineDropdown(
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
					primaryLoading = working,
					cancelEnabled = !working,
				)
			}
		}
	}
}

private val AVAILABLE_EXPORT_FORMATS = listOf(ExportFormat.Markdown, ExportFormat.Epub, ExportFormat.Pdf)

private fun ExportFormat.labelRes(): StringResource = when (this) {
	ExportFormat.Markdown -> Res.string.project_home_export_format_markdown
	ExportFormat.Epub -> Res.string.project_home_export_format_epub
	ExportFormat.Pdf -> Res.string.project_home_export_format_pdf
}
