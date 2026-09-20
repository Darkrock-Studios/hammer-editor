package com.darkrockstudios.apps.hammer.common.reauthentication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthentication
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get

private val DialogMaxWidth = 480.dp

@Composable
fun ReauthenticationUi(
	component: ServerReauthentication,
) {
	val state by component.state.subscribeAsState()

	var passwordVisible by rememberSaveable(state.showReauth) { mutableStateOf(false) }

	var renderInternal by remember { mutableStateOf(state.showReauth) }
	LaunchedEffect(state.showReauth) { if (state.showReauth) renderInternal = true }

	if (!renderInternal) return

	AnimatedDialogContainer(
		isOpen = state.showReauth,
		onDismissRequest = { component.cancelReauthentication() },
		onClosed = { renderInternal = false },
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		ReauthenticationContent(
			state = state,
			passwordVisible = passwordVisible,
			onPasswordVisibleChange = { passwordVisible = it },
			onPasswordChange = { component.updateServerPassword(it) },
			onClose = { component.cancelReauthentication() },
			onLogin = { component.reauthenticate(password = state.serverPassword) },
			modifier = Modifier.predictiveBackTransform(),
		)
	}
}

/**
 * The visual body of the re-auth dialog — masthead, server identity, password field, and
 * action row — split out from the [AnimatedDialogContainer] shell so it renders directly in
 * `@Preview` (the dialog opens its own window, which preview tooling can't capture).
 */
@Composable
internal fun ReauthenticationContent(
	state: ServerReauthentication.State,
	passwordVisible: Boolean,
	onPasswordVisibleChange: (Boolean) -> Unit,
	onPasswordChange: (String) -> Unit,
	onClose: () -> Unit,
	onLogin: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		modifier = modifier
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
				section = Res.string.reauth_title.get(),
				leadingMeta = listOf(Res.string.reauth_token_expired.get()),
				trailing = {
					HdMastheadAction(
						label = "× " + Res.string.close_dialog_button.get(),
						onClick = onClose,
					)
				},
			)
			HdFolioDivider()

			Header()

			if (state.serverWorking) {
				LinearProgressIndicator(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = Ui.Padding.XL)
						.height(2.dp),
					color = MaterialTheme.colorScheme.primary,
				)
			}

			Column(
				modifier = Modifier
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
					text = Res.string.reauth_explanation.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				HdMetadataItem(
					label = Res.string.reauth_server_url.get(),
					value = state.serverUrl,
					selectable = true,
				)

				HdMetadataItem(
					label = Res.string.reauth_server_email.get(),
					value = state.serverEmail,
					selectable = true,
				)

				HdPasswordField(
					label = Res.string.settings_server_setup_password_hint.get(),
					value = state.serverPassword,
					onValueChange = onPasswordChange,
					visible = passwordVisible,
					onVisibleChange = onPasswordVisibleChange,
					placeholder = Res.string.settings_server_setup_password_hint.get(),
					enabled = !state.serverWorking,
				)

				state.serverError?.let { error ->
					HdMonoLabel(
						text = "! $error",
						color = MaterialTheme.colorScheme.error,
					)
				}
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			ActionRow(
				working = state.serverWorking,
				onCancel = onClose,
				onLogin = onLogin,
			)
		}
	}
}

@Composable
private fun Header() {
	Text(
		text = Res.string.reauth_title.get(),
		style = MaterialTheme.typography.headlineSmall,
		color = MaterialTheme.colorScheme.onSurface,
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.L,
				bottom = Ui.Padding.S,
			),
	)
}

@Composable
private fun ActionRow(
	working: Boolean,
	onCancel: () -> Unit,
	onLogin: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdHairlineButton(
			label = Res.string.settings_server_setup_cancel_button.get(),
			onClick = onCancel,
			enabled = !working,
		)

		Spacer(modifier = Modifier.weight(1f))

		HdHairlineButton(
			label = Res.string.settings_server_setup_login_button.get(),
			onClick = onLogin,
			enabled = !working,
			emphasised = true,
		)
	}
}
