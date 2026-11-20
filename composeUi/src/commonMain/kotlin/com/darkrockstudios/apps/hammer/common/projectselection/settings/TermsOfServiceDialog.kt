package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_server_tos_accept
import com.darkrockstudios.apps.hammer.settings_server_tos_decline
import com.darkrockstudios.apps.hammer.settings_server_tos_title

private val DialogMaxWidth = 540.dp
private val DialogMaxHeight = 760.dp

@Composable
fun TermsOfServiceDialog(component: AccountSettings) {
	val state by component.state.subscribeAsState()
	val challenge = state.tosChallenge

	if (challenge != null) {
		AnimatedDialogContainer(
			isOpen = true,
			onDismissRequest = { component.declineTos() },
			onClosed = {},
			properties = DialogProperties(
				dismissOnBackPress = true,
				dismissOnClickOutside = false,
				usePlatformDefaultWidth = false,
			),
		) {
			TermsOfServiceDialogContent(
				text = challenge.text,
				server = state.serverUrl,
				working = state.serverWorking,
				onAccept = { component.acceptTos() },
				onDecline = { component.declineTos() },
				modifier = Modifier.predictiveBackTransform(),
			)
		}
	}
}

/**
 * The static chrome of [TermsOfServiceDialog] without the [AnimatedDialogContainer] wrapper, so it
 * can be rendered directly in a `@Preview` where the enter animation can't advance.
 */
@Composable
fun TermsOfServiceDialogContent(
	text: String,
	/** The server whose terms these are, named under the title. */
	server: String?,
	working: Boolean,
	onAccept: () -> Unit,
	onDecline: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		modifier = modifier
			.padding(Ui.Padding.XL)
			.widthIn(max = DialogMaxWidth)
			.heightIn(max = DialogMaxHeight)
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
			Column(
				modifier = Modifier.padding(start = Ui.Padding.XL, end = Ui.Padding.XL, top = Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text(
					text = Res.string.settings_server_tos_title.get(),
					style = MaterialTheme.typography.titleLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
				server?.let { HdMetaText(text = it) }
			}

			Text(
				text = text,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
					.padding(Ui.Padding.XL),
			)

			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
			) {
				HdHairlineButton(
					label = Res.string.settings_server_tos_decline.get(),
					onClick = onDecline,
					enabled = !working,
				)
				HdHairlineButton(
					label = Res.string.settings_server_tos_accept.get(),
					onClick = onAccept,
					enabled = !working,
					emphasised = true,
				)
			}
		}
	}
}
