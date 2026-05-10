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

private val DialogMaxWidth = 540.dp
private val DialogMaxHeight = 760.dp
private const val SECTION_COUNT = 4

@Composable
internal fun ServerSetupHelpDialog(onDismiss: () -> Unit) {
	val uriHandler = LocalUriHandler.current
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
		Surface(
			modifier = Modifier
				.padding(Ui.Padding.XL)
				.widthIn(max = DialogMaxWidth)
				.heightIn(max = DialogMaxHeight)
				.fillMaxWidth()
				.fillMaxHeight(0.9f)
				.predictiveBackTransform(),
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			border = BorderStroke(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			),
		) {
			Column(modifier = Modifier.fillMaxWidth()) {
				Masthead(onClose = ::requestDismiss)
				HdFolioDivider()

				Column(
					modifier = Modifier
						.weight(1f)
						.fillMaxWidth()
						.verticalScroll(rememberScrollState())
						.padding(
							start = Ui.Padding.XL,
							end = Ui.Padding.XL,
							top = Ui.Padding.L,
							bottom = Ui.Padding.L,
						),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					Text(
						text = Res.string.server_setup_help_title.get(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurface,
					)

					Text(
						text = Res.string.server_setup_help_intro.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					HdHairlineSection(
						section = 1,
						title = Res.string.server_setup_help_official_header.get(),
					) {
						Text(
							text = Res.string.server_setup_help_official_body.get(),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						Text(
							text = Res.string.server_setup_help_patreon.get(),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						Row(
							horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
						) {
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

					HdHairlineSection(
						section = 2,
						title = Res.string.server_setup_help_registration_header.get(),
					) {
						Text(
							text = Res.string.server_setup_help_registration_body.get(),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}

					HdHairlineSection(
						section = 3,
						title = Res.string.server_setup_help_setup_header.get(),
					) {
						SetupStep(index = 1, text = Res.string.server_setup_help_setup_step1.get())
						SetupStep(index = 2, text = Res.string.server_setup_help_setup_step2.get())
						SetupStep(index = 3, text = Res.string.server_setup_help_setup_step3.get())
					}

					HdCatalogueCard(topStart = "NOTE") {
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

				HdFolioDivider()
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							horizontal = Ui.Padding.XL,
							vertical = Ui.Padding.L,
						),
					horizontalArrangement = Arrangement.End,
				) {
					HdHairlineButton(
						label = Res.string.server_setup_help_dismiss_button.get(),
						onClick = ::requestDismiss,
						emphasised = true,
					)
				}
			}
		}
	}
}

@Composable
private fun Masthead(onClose: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = "§ HELP · SYNC SERVERS",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Box(
			modifier = Modifier
				.height(12.dp)
				.width(Dp.Hairline)
				.background(MaterialTheme.colorScheme.outlineVariant),
		)
		HdMonoLabel(text = "§§ $SECTION_COUNT")
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = "× CLOSE",
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.clickable(onClick = onClose)
				.padding(vertical = 4.dp, horizontal = 4.dp),
		)
	}
}

@Composable
private fun SetupStep(index: Int, text: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdMonoLabel(
			text = index.toString().padStart(2, '0'),
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.width(28.dp),
		)
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
