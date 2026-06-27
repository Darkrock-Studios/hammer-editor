package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val DialogMaxWidth = 480.dp

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
	var confirmDeleteLocal by rememberSaveable(state.serverSetup) { mutableStateOf<Boolean?>(null) }
	var showHelpDialog by rememberSaveable { mutableStateOf(false) }
	val existingServer = rememberSaveable(state.serverSetup) {
		state.serverWorking.not()
			&& state.currentUrl != null
			&& state.currentUserId != null
			&& state.currentUserId != -1L
	}

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
			Masthead(
				existingServer = existingServer,
				loggedIn = state.serverIsLoggedIn,
				onHelp = { showHelpDialog = true },
				onClose = { component.cancelServerSetup() },
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
				HdHairlineSegmentedPicker(
					options = listOf(false, true),
					selected = state.serverSsl,
					onSelect = { ssl ->
						if (!state.serverWorking && !existingServer) {
							component.updateServerSsl(ssl)
						}
					},
					label = { ssl -> if (ssl) "HTTPS" else "HTTP" },
					title = "PROTOCOL",
				)

				HdHairlineField(
					label = "URL",
					value = state.serverUrl ?: "",
					onValueChange = { component.updateServerUrl(it) },
					placeholder = Res.string.settings_server_setup_url_hint.get(),
					singleLine = true,
					imeAction = ImeAction.Next,
					keyboardType = KeyboardType.Uri,
					enabled = !state.serverWorking && !existingServer,
				)

				HdHairlineField(
					label = "EMAIL",
					value = state.serverEmail ?: "",
					onValueChange = { component.updateServerEmail(it) },
					placeholder = Res.string.settings_server_setup_email_hint.get(),
					singleLine = true,
					imeAction = ImeAction.Next,
					keyboardType = KeyboardType.Email,
					enabled = !state.serverWorking && !existingServer,
				)

				HdHairlineField(
					label = "PASSWORD",
					value = state.serverPassword ?: "",
					onValueChange = { component.updateServerPassword(it) },
					placeholder = Res.string.settings_server_setup_password_hint.get(),
					singleLine = true,
					imeAction = ImeAction.Done,
					keyboardType = KeyboardType.Password,
					visualTransformation = if (passwordVisible) VisualTransformation.None
					else PasswordVisualTransformation(),
					enabled = !state.serverWorking,
				)

				HdHairlineToggleRow(
					checked = passwordVisible,
					onCheckedChange = { passwordVisible = it },
					label = if (passwordVisible) {
						Res.string.settings_server_setup_password_hide.get()
					} else {
						Res.string.settings_server_setup_password_show.get()
					},
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
				isLoggedIn = state.serverIsLoggedIn,
				canCreate = state.currentUrl == null,
				onCancel = { scope.launch { component.cancelServerSetup() } },
				onCreate = { confirmDeleteLocal = true },
				onLogin = {
					if (state.serverIsLoggedIn.not()) {
						confirmDeleteLocal = false
					} else {
						component.setupServer(
							ssl = state.serverSsl,
							url = state.serverUrl ?: "",
							email = state.serverEmail ?: "",
							password = state.serverPassword ?: "",
							create = false,
							removeLocalContent = false,
						)
					}
				},
			)
		}
	}

	confirmDeleteLocal?.let { create ->
		fun setupServer(create: Boolean, removeLocal: Boolean) {
			component.setupServer(
				ssl = state.serverSsl,
				url = state.serverUrl ?: "",
				email = state.serverEmail ?: "",
				password = state.serverPassword ?: "",
				create = create,
				removeLocalContent = removeLocal,
			)
		}

		SimpleConfirm(
			title = Res.string.remove_local_dialog_title.get(),
			message = Res.string.remove_local_dialog_message.get(),
			implicitCancel = false,
			onDismiss = {
				setupServer(create, false)
				confirmDeleteLocal = null
			},
			onConfirm = {
				setupServer(create, true)
				confirmDeleteLocal = null
			},
		)
	}

	if (showHelpDialog) {
		ServerSetupHelpDialog(onDismiss = { showHelpDialog = false })
	}
}

@Composable
private fun Masthead(
	existingServer: Boolean,
	loggedIn: Boolean,
	onHelp: () -> Unit,
	onClose: () -> Unit,
) {
	val meta = buildList {
		if (existingServer) add("EXISTING")
		if (loggedIn) add("CONNECTED")
	}
	HdMasthead(
		section = "SERVER SETUP",
		leadingMeta = meta,
		trailing = {
			HdMastheadAction(label = "? HELP", onClick = onHelp)
			HdMastheadAction(label = "× CLOSE", onClick = onClose)
		},
	)
}

@Composable
private fun Header() {
	Text(
		text = Res.string.settings_server_setup_title.get(),
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
	isLoggedIn: Boolean,
	canCreate: Boolean,
	onCancel: () -> Unit,
	onCreate: () -> Unit,
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

		if (!isLoggedIn) {
			HdHairlineButton(
				label = Res.string.settings_server_setup_create_button.get(),
				onClick = onCreate,
				enabled = !working && canCreate,
			)
		}

		HdHairlineButton(
			label = Res.string.settings_server_setup_login_button.get(),
			onClick = onLogin,
			enabled = !working,
			emphasised = true,
		)
	}
}
