package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
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
) {
	val state by component.state.subscribeAsState()
	val focusManager = LocalFocusManager.current

	var passwordVisible by rememberSaveable(state.serverSetup) { mutableStateOf(false) }
	var confirmDeleteLocal by rememberSaveable(state.serverSetup) { mutableStateOf<Boolean?>(null) }
	val existingServer = rememberSaveable(state.serverSetup) { state.serverWorking.not() && state.currentUrl != null }

	if (state.serverSetup) {
		AlertDialog(
			onDismissRequest = { component.cancelServerSetup() },
			properties = DialogProperties(usePlatformDefaultWidth = false),
			modifier = Modifier.widthIn(max = 480.dp).padding(Ui.Padding.M)
		) {
			Surface(
				shape = RoundedCornerShape(16.dp),
				color = MaterialTheme.colorScheme.surface,
				tonalElevation = Ui.ToneElevation.MEDIUM
			) {
				Column(
					modifier = Modifier
						.padding(Ui.Padding.XL)
						.verticalScroll(rememberScrollState())
				) {
					Text(
						MR.strings.settings_server_setup_title.get(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurface
					)
					Spacer(modifier = Modifier.size(Ui.Padding.M))

					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(12.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
					) {
						Column(modifier = Modifier.padding(Ui.Padding.L)) {
							// Loading Indicator
							if (state.serverWorking) {
								LinearProgressIndicator(
									modifier = Modifier.fillMaxWidth().padding(bottom = Ui.Padding.M)
								)
							}

							// Protocol Dropdown & URL Row
							Row(
								verticalAlignment = Alignment.CenterVertically,
								modifier = Modifier
									.fillMaxWidth()
									.height(intrinsicSize = IntrinsicSize.Min)
							) {
								FilterChip(
									modifier = Modifier.fillMaxHeight().padding(top = Ui.Padding.M),
									selected = state.serverSsl,
									onClick = { component.updateServerSsl(!state.serverSsl) },
									label = { Text("HTTPS") },
									leadingIcon = {
										if (state.serverSsl) {
											Icon(
												imageVector = Icons.Filled.Check,
												contentDescription = "Secure"
											)
										} else {
											Icon(
												imageVector = Icons.Filled.Close,
												contentDescription = "Insecure"
											)
										}
									},
									colors = FilterChipDefaults.filterChipColors(
										selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
										selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
										selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
									)
								)

								// URL Input
								OutlinedTextField(
									value = state.serverUrl ?: "",
									onValueChange = { component.updateServerUrl(it) },
									label = { Text(MR.strings.settings_server_setup_url_hint.get()) },
									modifier = Modifier.weight(1f).moveFocusOnTab(),
									keyboardOptions = KeyboardOptions(
										autoCorrect = false,
										imeAction = ImeAction.Next,
										keyboardType = KeyboardType.Uri
									),
									keyboardActions = KeyboardActions(
										onNext = { focusManager.moveFocus(FocusDirection.Down) }
									),
									enabled = state.serverWorking.not() && existingServer.not(),
									singleLine = true
								)
							}

							Spacer(modifier = Modifier.size(Ui.Padding.M))

							// Email Input
							OutlinedTextField(
								value = state.serverEmail ?: "",
								onValueChange = { component.updateServerEmail(it) },
								label = { Text(MR.strings.settings_server_setup_email_hint.get()) },
								modifier = Modifier.fillMaxWidth().moveFocusOnTab(),
								keyboardOptions = KeyboardOptions(
									autoCorrect = false,
									imeAction = ImeAction.Next,
									keyboardType = KeyboardType.Email
								),
								keyboardActions = KeyboardActions(
									onNext = { focusManager.moveFocus(FocusDirection.Down) }
								),
								enabled = state.serverWorking.not() && existingServer.not(),
								singleLine = true
							)
							Spacer(modifier = Modifier.size(Ui.Padding.M))

							// Password Input
							OutlinedTextField(
								value = state.serverPassword ?: "",
								onValueChange = { component.updateServerPassword(it) },
								label = { Text(MR.strings.settings_server_setup_password_hint.get()) },
								singleLine = true,
								visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth().moveFocusOnTab(),
								keyboardOptions = KeyboardOptions(
									autoCorrect = false,
									imeAction = ImeAction.Done,
									keyboardType = KeyboardType.Password
								),
								keyboardActions = KeyboardActions(
									onDone = { focusManager.clearFocus() },
								),
								trailingIcon = {
									val image = if (passwordVisible)
										Icons.Filled.Visibility
									else Icons.Filled.VisibilityOff

									val description = if (passwordVisible)
										MR.strings.settings_server_setup_password_hide.get()
									else
										MR.strings.settings_server_setup_password_show.get()

									IconButton(onClick = { passwordVisible = !passwordVisible }) {
										Icon(imageVector = image, description)
									}
								},
								enabled = state.serverWorking.not()
							)
						}
					}

					// Error Message
					state.serverError?.let { error ->
						Spacer(modifier = Modifier.size(Ui.Padding.M))
						Text(
							error,
							color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.bodySmall,
							fontStyle = FontStyle.Italic,
							modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.Padding.S)
						)
					}

					Spacer(modifier = Modifier.size(Ui.Padding.XL))

					// Action Buttons
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.End
					) {
						// Cancel Button (Secondary)
						TextButton(
							onClick = { scope.launch { component.cancelServerSetup() } },
							enabled = state.serverWorking.not()
						) {
							Text(MR.strings.settings_server_setup_cancel_button.get())
						}

						Spacer(modifier = Modifier.weight(1f))

						// Create Button (if applicable)
						if (state.serverIsLoggedIn.not()) {
							Button(
								onClick = { confirmDeleteLocal = true },
								enabled = state.serverWorking.not() && state.currentUrl == null,
								colors = ButtonDefaults.filledTonalButtonColors()
							) {
								Text(MR.strings.settings_server_setup_create_button.get())
							}
							Spacer(modifier = Modifier.size(Ui.Padding.M))
						}

						// Log In Button (Primary)
						Button(
							onClick = {
								if (state.serverIsLoggedIn.not()) {
									confirmDeleteLocal = false
								} else {
									component.setupServer(
										ssl = state.serverSsl,
										url = state.serverUrl ?: "",
										email = state.serverEmail ?: "",
										password = state.serverPassword ?: "",
										create = false,
										removeLocalContent = false
									)
								}
							},
							enabled = state.serverWorking.not()
						) {
							Text(MR.strings.settings_server_setup_login_button.get())
						}
					}
				}
			}
		}
	}

	// Confirmation Dialog
	confirmDeleteLocal?.let { create ->
		fun setupServer(create: Boolean, removeLocal: Boolean) {
			component.setupServer(
				ssl = state.serverSsl,
				url = state.serverUrl ?: "",
				email = state.serverEmail ?: "",
				password = state.serverPassword ?: "",
				create = create,
				removeLocalContent = removeLocal
			)
		}

		SimpleConfirm(
			title = MR.strings.remove_local_dialog_title.get(),
			message = MR.strings.remove_local_dialog_message.get(),
			implicitCancel = false,
			onDismiss = {
				setupServer(create, false)
				confirmDeleteLocal = null
			},
			onConfirm = {
				setupServer(create, true)
				confirmDeleteLocal = null
			}
		)
	}
}