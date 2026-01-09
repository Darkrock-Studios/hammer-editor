package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.HAMMER_INK_URL
import com.darkrockstudios.apps.hammer.base.PATREON_URL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSetupHelpDialog(onDismiss: () -> Unit) {
	val uriHandler = LocalUriHandler.current

	BasicAlertDialog(
		onDismissRequest = onDismiss,
		modifier = Modifier.widthIn(max = 520.dp).padding(Ui.Padding.M),
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Surface(
			shape = RoundedCornerShape(16.dp),
			color = MaterialTheme.colorScheme.surface,
			tonalElevation = Ui.ToneElevation.MEDIUM
		) {
			Column(
				modifier = Modifier
					.padding(Ui.Padding.XL)
					.verticalScroll(rememberScrollState())
			) {
				// Title
				Text(
					Res.string.server_setup_help_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface
				)
				Spacer(modifier = Modifier.size(Ui.Padding.L))

				// Intro
				Text(
					Res.string.server_setup_help_intro.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(modifier = Modifier.size(Ui.Padding.L))

				// Official Server Section
				HelpSectionHeader(Res.string.server_setup_help_official_header.get())
				Text(
					Res.string.server_setup_help_official_body.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(modifier = Modifier.size(Ui.Padding.S))

				// Patreon link
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						Res.string.server_setup_help_patreon.get() + " ",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Row {
					TextButton(
						onClick = { uriHandler.openUri(PATREON_URL) },
						contentPadding = PaddingValues(horizontal = Ui.Padding.L)
					) {
						Text(
							Res.string.server_setup_help_patreon_link.get(),
							style = MaterialTheme.typography.bodyMedium.copy(
								textDecoration = TextDecoration.Underline
							)
						)
					}
					Spacer(modifier = Modifier.size(Ui.Padding.L))
					TextButton(
						onClick = { uriHandler.openUri(HAMMER_INK_URL) },
						contentPadding = PaddingValues(horizontal = Ui.Padding.L)
					) {
						Text(
							Res.string.server_setup_help_hammer_link.get(),
							style = MaterialTheme.typography.bodyMedium.copy(
								textDecoration = TextDecoration.Underline
							)
						)
					}
				}
				Spacer(modifier = Modifier.size(Ui.Padding.L))

				// Registration Section
				HelpSectionHeader(Res.string.server_setup_help_registration_header.get())
				Text(
					Res.string.server_setup_help_registration_body.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(modifier = Modifier.size(Ui.Padding.L))

				// Setup Section
				HelpSectionHeader(Res.string.server_setup_help_setup_header.get())
				Column(modifier = Modifier.padding(start = Ui.Padding.M)) {
					HelpBulletPoint("1.", Res.string.server_setup_help_setup_step1.get())
					HelpBulletPoint("2.", Res.string.server_setup_help_setup_step2.get())
					HelpBulletPoint("3.", Res.string.server_setup_help_setup_step3.get())
				}
				Spacer(modifier = Modifier.size(Ui.Padding.L))

				// Important Note Section
				Surface(
					modifier = Modifier.fillMaxWidth(),
					shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
					color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
				) {
					Column(modifier = Modifier.padding(Ui.Padding.M)) {
						Text(
							Res.string.server_setup_help_note_header.get(),
							style = MaterialTheme.typography.titleSmall,
							color = MaterialTheme.colorScheme.onTertiaryContainer
						)
						Spacer(modifier = Modifier.size(Ui.Padding.S))
						Text(
							Res.string.server_setup_help_note_body.get(),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onTertiaryContainer
						)
					}
				}
				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				// Dismiss Button
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					Button(onClick = onDismiss) {
						Text(Res.string.server_setup_help_dismiss_button.get())
					}
				}
			}
		}
	}
}

@Composable
private fun HelpSectionHeader(text: String) {
	Text(
		text,
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.primary
	)
	Spacer(modifier = Modifier.size(Ui.Padding.S))
}

@Composable
private fun HelpBulletPoint(number: String, text: String) {
	Row(modifier = Modifier.padding(vertical = 2.dp)) {
		Text(
			number,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.primary,
			modifier = Modifier.width(24.dp)
		)
		Text(
			text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}