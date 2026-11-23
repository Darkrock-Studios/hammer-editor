package com.darkrockstudios.apps.hammer.common.reauthentication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.account.MfaCodeStep
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthentication
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogActions
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogFrame
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get


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
			onMfaCodeSubmit = { component.submitMfaCode(it) },
			onMfaCancel = { component.cancelMfa() },
			modifier = Modifier.predictiveBackTransform(),
		)
	}
}

/**
 * The visual body of the re-auth dialog: masthead, server identity, password field (or the
 * second-factor code field while a challenge is pending), and action row. Split out from the
 * [AnimatedDialogContainer] shell so it renders directly in `@Preview` (the dialog opens its own
 * window, which preview tooling can't capture).
 */
@Composable
internal fun ReauthenticationContent(
	state: ServerReauthentication.State,
	passwordVisible: Boolean,
	onPasswordVisibleChange: (Boolean) -> Unit,
	onPasswordChange: (String) -> Unit,
	onClose: () -> Unit,
	onLogin: () -> Unit,
	onMfaCodeSubmit: (String) -> Unit,
	onMfaCancel: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val challenge = state.mfaChallenge
	var mfaCode by rememberSaveable(challenge) { mutableStateOf("") }
	var useRecoveryCode by rememberSaveable(challenge) { mutableStateOf(false) }

	HdDialogFrame(modifier = modifier, maxWidth = Ui.MaxWidth.DialogNarrow) {
		Column {
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

				if (challenge != null) {
					MfaCodeStep(
						challenge = challenge,
						code = mfaCode,
						onCodeChange = { mfaCode = it },
						useRecoveryCode = useRecoveryCode,
						onUseRecoveryCodeChange = { useRecoveryCode = it },
						enabled = !state.serverWorking,
					)
				} else {
					HdPasswordField(
						label = Res.string.settings_server_setup_password_hint.get(),
						value = state.serverPassword,
						onValueChange = onPasswordChange,
						visible = passwordVisible,
						onVisibleChange = onPasswordVisibleChange,
						placeholder = Res.string.settings_server_setup_password_hint.get(),
						enabled = !state.serverWorking,
					)
				}

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

			if (challenge != null) {
				ActionRow(
					working = state.serverWorking,
					cancelLabel = Res.string.settings_server_setup_mfa_back_button.get(),
					confirmLabel = Res.string.settings_server_setup_mfa_verify_button.get(),
					canConfirm = mfaCode.isNotBlank(),
					onCancel = onMfaCancel,
					onConfirm = { onMfaCodeSubmit(mfaCode) },
				)
			} else {
				ActionRow(
					working = state.serverWorking,
					cancelLabel = Res.string.settings_server_setup_cancel_button.get(),
					confirmLabel = Res.string.settings_server_setup_login_button.get(),
					canConfirm = true,
					onCancel = onClose,
					onConfirm = onLogin,
				)
			}
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
				top = Ui.Padding.XL,
				bottom = Ui.Padding.S,
			),
	)
}

@Composable
private fun ActionRow(
	working: Boolean,
	cancelLabel: String,
	confirmLabel: String,
	canConfirm: Boolean,
	onCancel: () -> Unit,
	onConfirm: () -> Unit,
) {
	HdDialogActions(
		cancelLabel = cancelLabel,
		onCancel = onCancel,
		confirmLabel = confirmLabel,
		onConfirm = onConfirm,
		modifier = Modifier.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		confirmEnabled = !working && canConfirm,
		cancelEnabled = !working,
	)
}
