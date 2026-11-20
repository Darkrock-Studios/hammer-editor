package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.HAMMER_INK_URL
import com.darkrockstudios.apps.hammer.base.PATREON_URL
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.server_setup_help_note_caption
import com.darkrockstudios.apps.hammer.Res

private val DialogMaxHeight = 760.dp

@Composable
internal fun ServerSetupHelpDialog(onDismiss: () -> Unit) {
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
		ServerSetupHelpContent(onClose = ::requestDismiss, modifier = Modifier.predictiveBackTransform())
	}
}

/** The help card without its dialog window, so previews can draw it. */
@Composable
internal fun ServerSetupHelpContent(onClose: () -> Unit, modifier: Modifier = Modifier) {
	val uriHandler = LocalUriHandler.current
	HdHairlineDialogShell(
		title = Res.string.server_setup_help_title.get(),
		onClose = onClose,
		closeContentDescription = Res.string.server_setup_help_close.get(),
		modifier = modifier.heightIn(max = DialogMaxHeight),
	) {
		Column(
			modifier = Modifier
				.weight(1f, fill = false)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(28.dp),
		) {
			BodyText(Res.string.server_setup_help_intro.get())

			Topic(section = "§ I", title = Res.string.server_setup_help_official_header.get()) {
				BodyText(Res.string.server_setup_help_official_body.get())
				Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
					HdHairlineButton(
						label = Res.string.server_setup_help_patreon_link.get(),
						onClick = { uriHandler.openUri(PATREON_URL) },
					)
					HdHairlineButton(
						label = Res.string.server_setup_help_hammer_link.get(),
						onClick = { uriHandler.openUri(HAMMER_INK_URL) },
					)
				}
			}

			Topic(section = "§ II", title = Res.string.server_setup_help_registration_header.get()) {
				BodyText(Res.string.server_setup_help_registration_body.get())
			}

			HdCatalogueCard(topStart = Res.string.server_setup_help_note_caption.get()) {
				Text(
					text = Res.string.server_setup_help_note_header.get(),
					style = MaterialTheme.typography.titleSmall,
					color = MaterialTheme.colorScheme.onSurface,
				)
				Spacer(modifier = Modifier.height(Ui.Padding.S))
				Text(
					text = Res.string.server_setup_help_note_body.get(),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

/** A help topic: a § kicker beside a plain title, then its text. */
@Composable
private fun Topic(section: String, title: String, content: @Composable ColumnScope.() -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
			HdMonoLabel(text = section)
			Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
		}
		content()
	}
}

@Composable
private fun BodyText(text: String) {
	Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
