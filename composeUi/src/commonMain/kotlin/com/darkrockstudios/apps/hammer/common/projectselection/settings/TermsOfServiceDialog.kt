package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
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
			.fillMaxWidth()
			.fillMaxHeight(0.9f),
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
				section = "TERMS OF SERVICE",
				trailing = { HdMastheadAction(label = "× DECLINE", onClick = onDecline) },
			)
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
					text = Res.string.settings_server_tos_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
				)

				Text(
					text = text,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}

			HdFolioDivider()
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				HdHairlineButton(
					label = Res.string.settings_server_tos_decline.get(),
					onClick = onDecline,
					enabled = !working,
				)
				Spacer(modifier = Modifier.weight(1f))
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
