package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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

private val DialogMaxWidth = 480.dp

/** Animated wrapper around [ImportHelpContent]. Preview [ImportHelpContent] directly for a static frame. */
@Composable
internal fun ImportHelpDialog(onDismiss: () -> Unit) {
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
			ImportHelpContent(onDismiss = { isOpen = false })
		}
	}
}

@Composable
internal fun ImportHelpContent(onDismiss: () -> Unit) {
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
				section = "HELP · IMPORT",
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
					text = Res.string.create_project_import_help_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
				)
				Text(
					text = Res.string.create_project_import_help_intro.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
					HdMonoLabel(
						text = Res.string.create_project_import_help_formats_header.get(),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = Res.string.create_project_import_help_format_markdown.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurface,
					)
					Text(
						text = Res.string.create_project_import_help_format_rtf.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurface,
					)
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
					label = Res.string.create_project_import_help_dismiss.get(),
					onClick = onDismiss,
					emphasised = true,
				)
			}
		}
	}
}
