package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.projectselection.ServerSetupDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ServerSettingsUi(
	component: AccountSettings,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	var showConfirmRemoveServer by rememberSaveable { mutableStateOf(false) }

	val successColor = Color(0xFF4CAF50)
	val dividerColor = MaterialTheme.colorScheme.outlineVariant

	Column(
		modifier = Modifier.padding(Ui.Padding.M)
	) {
		Text(
			MR.strings.settings_server_header.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Text(
			MR.strings.settings_server_description.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onBackground,
			fontStyle = FontStyle.Italic
		)

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		if (state.serverIsLoggedIn.not()) {
			// Disconnected State
			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(12.dp),
				color = MaterialTheme.colorScheme.surfaceVariant,
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
			) {
				Column(
					modifier = Modifier.padding(Ui.Padding.XL),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Icon(
						Icons.Default.CloudSync,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(64.dp)
					)
					Spacer(modifier = Modifier.size(Ui.Padding.M))

					Text(
						MR.strings.settings_server_enable_sync_backup.get(),
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						fontWeight = FontWeight.Bold
					)
					Spacer(modifier = Modifier.size(Ui.Padding.S))

					Text(
						MR.strings.settings_server_enable_sync_backup_desc.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center
					)
					Spacer(modifier = Modifier.size(Ui.Padding.L))

					Button(
						onClick = { scope.launch { component.beginSetupServer() } },
						modifier = Modifier.fillMaxWidth()
					) {
						Text(MR.strings.settings_server_setup_button.get())
					}
				}
			}

		} else {
			// Logged In State
			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(12.dp),
				color = MaterialTheme.colorScheme.surfaceVariant,
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
			) {
				Column(modifier = Modifier.padding(Ui.Padding.L)) {
					Text(
						MR.strings.settings_server_connection_header.get(),
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(
						state.currentEmail ?: MR.strings.settings_server_unknown_error.get(),
						style = MaterialTheme.typography.headlineSmall,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(
						state.currentUrl ?: MR.strings.settings_server_unknown_error.get(),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
					)
					Spacer(modifier = Modifier.size(Ui.Padding.M))
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(
							Icons.Default.CheckCircle,
							contentDescription = null,
							tint = successColor,
							modifier = Modifier.size(16.dp)
						)
						Spacer(modifier = Modifier.size(Ui.Padding.S))
						Text(
							MR.strings.settings_server_connected.get(),
							style = MaterialTheme.typography.bodyMedium,
							color = successColor
						)
					}
					Spacer(modifier = Modifier.size(Ui.Padding.L))
					Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
						FilledTonalButton(onClick = {
							scope.launch {
								if (component.authTest()) {
									rootSnackbar.showSnackbar(strRes.get(MR.strings.settings_server_authtest_toast_success))
								} else {
									rootSnackbar.showSnackbar(strRes.get(MR.strings.settings_server_authtest_toast_failure))
								}
							}
						}) {
							Text(MR.strings.settings_server_test_auth_button.get())
						}
						FilledTonalButton(onClick = { component.reauthenticate() }) {
							Text(MR.strings.settings_server_reauth_button.get())
						}
					}
				}
			}

			Spacer(modifier = Modifier.size(Ui.Padding.L))

			HorizontalDivider(Modifier, DividerDefaults.Thickness, color = dividerColor)
			Spacer(modifier = Modifier.size(Ui.Padding.L))

			Text(
				MR.strings.settings_server_sync_backup_preferences.get(),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onBackground
			)
			Spacer(modifier = Modifier.size(Ui.Padding.M))

			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					var autoSyncValue by remember(state.syncAutomaticSync) { mutableStateOf(state.syncAutomaticSync) }
					Checkbox(
						checked = autoSyncValue,
						onCheckedChange = {
							scope.launch { component.setAutoSyncing(it) }
							autoSyncValue = it
						}
					)
					Text(
						MR.strings.settings_server_auto_sync.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onBackground,
					)
				}
				Row(verticalAlignment = Alignment.CenterVertically) {
					var autoCloseValue by remember(state.syncAutoCloseDialog) { mutableStateOf(state.syncAutoCloseDialog) }
					Checkbox(
						checked = autoCloseValue,
						onCheckedChange = {
							scope.launch { component.setAutoCloseDialogs(it) }
							autoCloseValue = it
						}
					)
					Text(
						MR.strings.settings_server_sync_auto_close.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onBackground,
					)
				}
				Spacer(modifier = Modifier.size(Ui.Padding.S))
				Column {
					Row(verticalAlignment = Alignment.CenterVertically) {
						var autoBackupsValue by remember(state.syncAutomaticBackups) { mutableStateOf(state.syncAutomaticBackups) }
						Checkbox(
							checked = autoBackupsValue,
							onCheckedChange = {
								scope.launch { component.setAutomaticBackups(it) }
								autoBackupsValue = it
							}
						)
						Text(
							MR.strings.settings_server_sync_backup.get(),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onBackground,
						)
					}
					Box(modifier = Modifier.padding(start = Ui.Padding.XL + Ui.Padding.M, top = Ui.Padding.S)) {
						var maxBackupsValue by remember(state.maxBackups) { mutableStateOf("${state.maxBackups}") }
						OutlinedTextField(
							modifier = Modifier.width(140.dp),
							value = maxBackupsValue,
							onValueChange = { newText ->
								if (newText.isEmpty() || newText.all { it.isDigit() }) {
									maxBackupsValue = newText
									newText.toIntOrNull()?.let { value ->
										if (value >= 0) {
											scope.launch { component.setMaxBackups(value) }
										}
									}
								}
							},
							label = { Text(MR.strings.settings_server_max_backups.get()) },
							singleLine = true,
						)
					}
				}
			}

			Spacer(modifier = Modifier.size(Ui.Padding.XL))
			HorizontalDivider(Modifier, DividerDefaults.Thickness, color = dividerColor)
			Spacer(modifier = Modifier.size(Ui.Padding.L))

			Text(
				MR.strings.settings_server_danger_zone.get(),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.error
			)
			Spacer(modifier = Modifier.size(Ui.Padding.S))
			OutlinedButton(
				onClick = { showConfirmRemoveServer = true },
				colors = ButtonDefaults.outlinedButtonColors(
					contentColor = MaterialTheme.colorScheme.error,
					containerColor = Color.Transparent
				),
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
				modifier = Modifier.fillMaxWidth()
			) {
				Text(MR.strings.settings_server_remove_server_button.get())
			}

			Spacer(modifier = Modifier.size(Ui.Padding.XL))
		}
	}
	if (showConfirmRemoveServer) {
		SimpleConfirm(
			title = MR.strings.settings_remove_server_dialog_title.get(),
			message = MR.strings.settings_remove_server_dialog_message.get(),
			onDismiss = { showConfirmRemoveServer = false },
			onConfirm = {
				scope.launch {
					component.removeServer()
					showConfirmRemoveServer = false
				}
			},
		)
	}

	Toaster(component, rootSnackbar)
	ServerSetupDialog(component, scope)
}