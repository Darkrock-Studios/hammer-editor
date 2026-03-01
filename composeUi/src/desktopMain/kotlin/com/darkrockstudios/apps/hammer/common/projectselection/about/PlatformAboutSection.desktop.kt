package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_logs_directory_label
import com.darkrockstudios.apps.hammer.about_logs_header
import com.darkrockstudios.apps.hammer.about_logs_open_tooltip
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.getLogDirectory

@Composable
actual fun PlatformAboutSection(component: AboutApp) {
	val logDir = getLogDirectory()

	if (logDir != null) {
		Spacer(modifier = Modifier.size(Ui.Padding.XL))

		Text(
			text = Res.string.about_logs_header.get(),
			style = MaterialTheme.typography.headlineSmall,
		)

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			IconButton(
				onClick = { component.openLogDirectory() },
				modifier = Modifier.size(36.dp)
			) {
				Icon(
					imageVector = Icons.Default.FolderOpen,
					contentDescription = Res.string.about_logs_open_tooltip.get(),
					tint = MaterialTheme.colorScheme.primary
				)
			}

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = Res.string.about_logs_directory_label.get(),
					style = MaterialTheme.typography.bodyMedium,
				)
				Spacer(modifier = Modifier.size(Ui.Padding.S))

				// Selectable text for the path
				SelectionContainer {
					Text(
						text = logDir,
						style = MaterialTheme.typography.bodySmall,
						fontFamily = FontFamily.Monospace,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			Spacer(modifier = Modifier.size(Ui.Padding.M))
		}
	}
}
