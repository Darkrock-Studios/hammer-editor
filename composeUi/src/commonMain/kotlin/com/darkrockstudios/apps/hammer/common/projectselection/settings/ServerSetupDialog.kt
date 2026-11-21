package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.common.account.MfaCodeStep
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogFrame
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
fun ServerSetupDialog(
	component: AccountSettings,
	scope: CoroutineScope,
) {
	val state by component.state.subscribeAsState()

	var renderInternal by remember { mutableStateOf(state.serverSetup) }
	LaunchedEffect(state.serverSetup) { if (state.serverSetup) renderInternal = true }

	RequestLocalNetworkPermission(show = state.serverSetup)

	if (renderInternal) {
		AnimatedDialogContainer(
			isOpen = state.serverSetup,
			onDismissRequest = { component.cancelServerSetup() },
			onClosed = { renderInternal = false },
			properties = DialogProperties(usePlatformDefaultWidth = false),
		) {
			ServerSetupDialogContent(
				component = component,
				scope = scope,
				modifier = Modifier.predictiveBackTransform(),
			)
		}
	}
}

/**
 * The static chrome of [ServerSetupDialog] without the [AnimatedDialogContainer] wrapper.
 * [ServerSetupDialog] animates this in/out; render it directly (e.g. in a `@Preview`) to capture
 * the dialog as a settled, opaque frame, since the Desktop preview renderer can't advance the
 * enter animation.
 */
@Composable
fun ServerSetupDialogContent(
	component: AccountSettings,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()

	var passwordVisible by rememberSaveable(state.serverSetup) { mutableStateOf(false) }
	var showHelpDialog by rememberSaveable { mutableStateOf(false) }
	val challenge = state.mfaChallenge
	var mfaCode by rememberSaveable(challenge) { mutableStateOf("") }
	var useRecoveryCode by rememberSaveable(challenge) { mutableStateOf(false) }
	val existingServer = rememberSaveable(state.serverSetup) {
		state.serverWorking.not()
			&& state.currentUrl != null
			&& state.currentUserId != null
			&& state.currentUserId != -1L
	}

	HdDialogFrame(modifier = modifier, maxWidth = Ui.MaxWidth.DialogNarrow) {
		Column {
			TitleRow(
				title = if (challenge != null) {
					Res.string.settings_server_setup_mfa_title.get()
				} else {
					Res.string.settings_server_setup_title.get()
				},
				stamp = when {
					challenge != null -> Res.string.settings_server_setup_mfa_step_stamp.get()
					state.serverIsLoggedIn -> Res.string.settings_server_setup_connected_stamp.get()
					else -> null
				},
				onHelp = { showHelpDialog = true },
			)

			if (challenge != null) {
				HdMetaText(
					text = Res.string.settings_server_setup_mfa_account.get(state.serverEmail ?: "", state.serverUrl ?: ""),
					modifier = Modifier.padding(horizontal = Ui.Padding.XL),
				)
			}

			if (state.serverWorking) {
				LinearProgressIndicator(
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = Ui.Padding.XL, end = Ui.Padding.XL, top = Ui.Padding.M)
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
						top = Ui.Padding.XL,
						bottom = Ui.Padding.XL,
					),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
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
					HdHairlineField(
						label = Res.string.settings_server_setup_url_label.get(),
						value = state.serverUrl ?: "",
						onValueChange = { component.updateServerUrl(it) },
						placeholder = Res.string.settings_server_setup_url_hint.get(),
						singleLine = true,
						imeAction = ImeAction.Next,
						capitalization = KeyboardCapitalization.None,
						autoCorrectEnabled = false,
						keyboardType = KeyboardType.Uri,
						enabled = !state.serverWorking && !existingServer,
					)

					if (state.serverUrlInsecure) {
						ErrorText(Res.string.settings_server_setup_insecure_warning.get())
					}

					HdEmailField(
						label = Res.string.settings_server_setup_email_label.get(),
						value = state.serverEmail ?: "",
						onValueChange = { component.updateServerEmail(it) },
						placeholder = Res.string.settings_server_setup_email_hint.get(),
						enabled = !state.serverWorking && !existingServer,
					)

					HdPasswordField(
						label = Res.string.settings_server_setup_password_label.get(),
						value = state.serverPassword ?: "",
						onValueChange = { component.updateServerPassword(it) },
						visible = passwordVisible,
						onVisibleChange = { passwordVisible = it },
						hint = Res.string.settings_server_setup_password_requirements.get(
							PasswordValidator.MIN_LENGTH,
							PasswordValidator.MAX_LENGTH,
						),
						enabled = !state.serverWorking,
					)
				}

				state.serverError?.let { ErrorText(it) }
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			// The component decides whether a login needs the merge/replace prompt; the UI only
			// ever asks for the non-destructive setup.
			fun setupServer(create: Boolean) {
				component.setupServer(
					url = state.serverUrl ?: "",
					email = state.serverEmail ?: "",
					password = state.serverPassword ?: "",
					create = create,
					replaceLocalContent = false,
				)
			}

			ActionRow {
				if (challenge != null) {
					HdHairlineButton(
						label = Res.string.settings_server_setup_mfa_back_button.get(),
						onClick = { component.cancelMfa() },
						enabled = !state.serverWorking,
					)
					HdHairlineButton(
						label = Res.string.settings_server_setup_mfa_verify_button.get(),
						onClick = { component.submitMfaCode(mfaCode) },
						enabled = !state.serverWorking && mfaCode.isNotBlank(),
						emphasised = true,
					)
				} else {
					HdHairlineButton(
						label = Res.string.settings_server_setup_cancel_button.get(),
						onClick = { scope.launch { component.cancelServerSetup() } },
						enabled = !state.serverWorking,
					)
					if (!state.serverIsLoggedIn) {
						HdHairlineButton(
							label = Res.string.settings_server_setup_create_button.get(),
							onClick = { setupServer(create = true) },
							enabled = !state.serverWorking && state.currentUrl == null,
						)
					}
					HdHairlineButton(
						label = Res.string.settings_server_setup_login_button.get(),
						onClick = { setupServer(create = false) },
						enabled = !state.serverWorking,
						emphasised = true,
					)
				}
			}
		}
	}

	if (showHelpDialog) {
		ServerSetupHelpDialog(onDismiss = { showHelpDialog = false })
	}
}

@Composable
private fun TitleRow(title: String, stamp: String?, onHelp: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = Ui.Padding.XL, end = Ui.Padding.XL, top = Ui.Padding.XL, bottom = Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleLarge,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
		)
		stamp?.let { HdMonoLabel(text = it) }
		HdHelpButton(onClick = onHelp, contentDescription = Res.string.settings_server_setup_help_description.get())
	}
}

@Composable
private fun ErrorText(text: String) {
	Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(buttons: @Composable () -> Unit) {
	FlowRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		buttons()
	}
}
