package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get

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
fun ImportHelpContent(onDismiss: () -> Unit) {
	HdHairlineDialogShell(
		title = Res.string.create_project_import_help_title.get(),
		onClose = onDismiss,
		closeContentDescription = Res.string.create_project_import_help_close.get(),
	) {
		Text(
			text = Res.string.create_project_import_help_intro.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(Modifier.height(24.dp))
		HdMonoLabel(
			text = Res.string.create_project_import_help_formats_header.get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(Modifier.height(8.dp))
		Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
			FormatLine(Res.string.create_project_import_help_format_markdown.get())
			FormatLine(Res.string.create_project_import_help_format_rtf.get())
		}
	}
}

@Composable
private fun FormatLine(text: String) {
	Row {
		Text(
			text = "•",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(18.dp),
		)
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}
