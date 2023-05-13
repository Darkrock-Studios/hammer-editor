package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.moveFocusOnTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupDialog(
	component: AccountSettings,
	scope: CoroutineScope,
	snackbarHostState: SnackbarHostState
) {
	val state by component.state.subscribeAsState()

	val focusManager = LocalFocusManager.current

	var sslValue by rememberSaveable { mutableStateOf(true) }
	var urlValue by rememberSaveable { mutableStateOf("") }
	var emailValue by rememberSaveable { mutableStateOf("") }
	var passwordValue by rememberSaveable { mutableStateOf("") }
	var passwordVisible by rememberSaveable { mutableStateOf(false) }

	fun clearInput() {
		sslValue = true
		urlValue = ""
		emailValue = ""
		passwordValue = ""
		passwordVisible = false
	}

	MpDialog(
		onCloseRequest = {
			clearInput()
			component.cancelServerSetup()
		},
		visible = state.serverSetup,
		title = MR.strings.settings_server_setup_title.get(),
		size = DpSize(400.dp, 460.dp)
	) {
		DisposableEffect(Unit) {
			onDispose {
				clearInput()
			}
		}

		LaunchedEffect(state.toast) {
			if (state.toast != null) {
				snackbarHostState.showSnackbar("Failed to setup server")
			}
		}

		Box(
			modifier = Modifier.padding(Ui.Padding.XL),
			contentAlignment = Alignment.Center
		) {
			if (state.serverWorking) {
				CircularProgressIndicator(
					modifier = Modifier.align(Alignment.Center).size(128.dp)
				)
			}

			Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
				Text(
					"Configure Server",
					style = MaterialTheme.typography.headlineMedium
				)

				//Divider(modifier = Modifier.fillMaxWidth())

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				Row(verticalAlignment = Alignment.CenterVertically) {
					Checkbox(
						checked = sslValue,
						onCheckedChange = { sslValue = it },
						enabled = state.serverWorking.not()
					)
					Text(MR.strings.settings_server_setup_ssl_label.get())
				}

				OutlinedTextField(
					value = urlValue,
					onValueChange = { urlValue = it },
					label = { Text(MR.strings.settings_server_setup_url_hint.get()) },
					modifier = Modifier.moveFocusOnTab(),
					keyboardOptions = KeyboardOptions(
						autoCorrect = false,
						imeAction = ImeAction.Next,
						keyboardType = KeyboardType.Uri
					),
					keyboardActions = KeyboardActions(
						onNext = { focusManager.moveFocus(FocusDirection.Down) }
					),
					enabled = state.serverWorking.not()
				)

				OutlinedTextField(
					value = emailValue,
					onValueChange = { emailValue = it },
					label = { Text(MR.strings.settings_server_setup_email_hint.get()) },
					modifier = Modifier.moveFocusOnTab(),
					keyboardOptions = KeyboardOptions(
						autoCorrect = false,
						imeAction = ImeAction.Next,
						keyboardType = KeyboardType.Email
					),
					keyboardActions = KeyboardActions(
						onNext = { focusManager.moveFocus(FocusDirection.Down) }
					),
					enabled = state.serverWorking.not()
				)

				OutlinedTextField(
					value = passwordValue,
					onValueChange = { passwordValue = it },
					label = { Text(MR.strings.settings_server_setup_password_hint.get()) },
					singleLine = true,
					placeholder = { Text(MR.strings.settings_server_setup_password_hint.get()) },
					visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
					modifier = Modifier.moveFocusOnTab(),
					keyboardOptions = KeyboardOptions(
						autoCorrect = false,
						imeAction = ImeAction.Done,
						keyboardType = KeyboardType.Password
					),
					keyboardActions = KeyboardActions(
						onNext = { focusManager.moveFocus(FocusDirection.Down) },
					),
					trailingIcon = {
						val image = if (passwordVisible)
							Icons.Filled.Visibility
						else Icons.Filled.VisibilityOff

						// Please provide localized description for accessibility services
						val description = if (passwordVisible) "Hide password" else "Show password"

						IconButton(onClick = { passwordVisible = !passwordVisible }) {
							Icon(imageVector = image, description)
						}
					},
					enabled = state.serverWorking.not()
				)

				state.serverError?.let { error ->
					Text(
						error,
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall,
						fontStyle = FontStyle.Italic
					)
				}

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				Row {
					Button(
						onClick = {
							component.setupServer(
								ssl = sslValue,
								url = urlValue,
								email = emailValue,
								password = passwordValue,
								create = false
							)
						},
						enabled = state.serverWorking.not()
					) {
						Text(MR.strings.settings_server_setup_login_button.get())
					}

					Button(
						onClick = {
							component.setupServer(
								ssl = sslValue,
								url = urlValue,
								email = emailValue,
								password = passwordValue,
								create = true
							)
						},
						enabled = state.serverWorking.not()
					) {
						Text(MR.strings.settings_server_setup_create_button.get())
					}

					Button(
						onClick = {
							scope.launch {
								clearInput()
								component.cancelServerSetup()
							}
						},
						enabled = state.serverWorking.not()
					) {
						Text(MR.strings.settings_server_setup_cancel_button.get())
					}
				}
			}
		}
	}
}