package com.darkrockstudios.apps.hammer.common.reauthentication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthentication
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moveFocusOnTab
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReauthenticationUi(
	component: ServerReauthentication,
) {
	val state by component.state.subscribeAsState()

	val focusManager = LocalFocusManager.current

	var passwordVisible by rememberSaveable { mutableStateOf(false) }

	SimpleDialog(
		onCloseRequest = {
			component.cancelReauthentication()
		},
		visible = state.showReauth,
		title = Res.string.reauth_title.get(),
	) {
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
					Res.string.reauth_explanation.get(),
					style = MaterialTheme.typography.bodyMedium
				)

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				Text(
					Res.string.reauth_server_url.get(),
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Bold
				)
				Text(
					state.serverUrl,
					style = MaterialTheme.typography.bodyMedium,
					fontWeight = FontWeight.Thin
				)

				Text(
					Res.string.reauth_server_email.get(),
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Bold
				)
				Text(
					state.serverEmail,
					style = MaterialTheme.typography.bodyMedium,
					fontWeight = FontWeight.Thin
				)

				SpacerXL()

				OutlinedTextField(
					value = state.serverPassword,
					onValueChange = { component.updateServerPassword(it) },
					label = { Text(Res.string.settings_server_setup_password_hint.get()) },
					singleLine = true,
					placeholder = { Text(Res.string.settings_server_setup_password_hint.get()) },
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
						val description = if (passwordVisible)
							Res.string.settings_server_setup_password_hide.get()
						else
							Res.string.settings_server_setup_password_show.get()

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

				Row(
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(
						onClick = {
							component.reauthenticate(password = state.serverPassword)
						},
						enabled = state.serverWorking.not()
					) {
						Text(Res.string.settings_server_setup_login_button.get())
					}

					SpacerXL()

					Button(
						onClick = {
							component.cancelReauthentication()
						},
						enabled = state.serverWorking.not()
					) {
						Text(Res.string.settings_server_setup_cancel_button.get())
					}
				}
			}
		}
	}
}