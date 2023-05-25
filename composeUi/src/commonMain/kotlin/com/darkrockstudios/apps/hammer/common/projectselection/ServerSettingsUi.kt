package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsUi(component: AccountSettings, scope: CoroutineScope, snackbarHostState: SnackbarHostState) {
	val state by component.state.subscribeAsState()
	var showConfirmRemoveServer by rememberSaveable { mutableStateOf(false) }

	Column(modifier = Modifier.padding(Ui.Padding.M)) {
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

		if (state.serverIsLoggedIn.not()) {
			Button(onClick = {
				scope.launch {
					component.beginSetupServer()
				}
			}) {
				Text(MR.strings.settings_server_setup_button.get())
			}
		} else {
			Row {
				Text(
					MR.strings.settings_server_url_label.get(),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onBackground,
				)

				Text(
					state.serverUrl ?: "error",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onBackground,
					fontStyle = FontStyle.Italic
				)
			}

			Row {
				Text(
					MR.strings.settings_server_email_label.get(),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onBackground,
				)

				Text(
					state.serverEmail ?: "error",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onBackground,
					fontStyle = FontStyle.Italic
				)
			}

			Row {
				var autoSyncValue by remember { mutableStateOf(state.syncAutomaticSync) }
				Checkbox(
					checked = autoSyncValue,
					onCheckedChange = {
						scope.launch { component.setAutoSyncing(it) }
						autoSyncValue = it
					}
				)
				Text(
					"Auto-Sync",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.align(Alignment.CenterVertically)
				)
			}

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			Row {
				var autoBackupsValue by remember { mutableStateOf(state.syncAutomaticBackups) }
				Checkbox(
					checked = autoBackupsValue,
					onCheckedChange = {
						scope.launch { component.setAutomaticBackups(it) }
						autoBackupsValue = it
					}
				)
				Text(
					"Backup on sync",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.align(Alignment.CenterVertically)
				)
			}

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			Row {
				var autoCloseValue by remember { mutableStateOf(state.syncAutoCloseDialog) }
				Checkbox(
					checked = autoCloseValue,
					onCheckedChange = {
						scope.launch { component.setAutoCloseDialogs(it) }
						autoCloseValue = it
					}
				)
				Text(
					"Close Sync Dialogs on Success",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.align(Alignment.CenterVertically)
				)
			}

			Spacer(modifier = Modifier.size(Ui.Padding.L))

			var maxBackupsValue by remember { mutableStateOf("${state.maxBackups}") }
			OutlinedTextField(
				modifier = Modifier.width(128.dp),
				value = maxBackupsValue,
				onValueChange = {
					val value = it.toIntOrNull()
					if (value != null && value >= 0) {
						maxBackupsValue = it
						scope.launch { component.setMaxBackups(value) }
					}
				},
				label = { Text("Max Backups per Project") },
			)

			Spacer(modifier = Modifier.size(Ui.Padding.L))

			Button(onClick = {
				scope.launch {
					if (component.authTest()) {
						snackbarHostState.showSnackbar("Auth Test Successful")
					} else {
						snackbarHostState.showSnackbar("Auth Test Failed")
					}
				}
			}) {
				//Text(MR.strings.settings_server_modify_button.get())
				Text("Test Auth")
			}
			Button(onClick = { component.reauthenticate() }) {
				//Text(MR.strings.settings_server_modify_button.get())
				Text("Re-Auth")
			}

			Spacer(modifier = Modifier.size(Ui.Padding.L))

			Button(onClick = {
				showConfirmRemoveServer = true
			}) {
				//Text(MR.strings.settings_server_modify_button.get())
				Text("Remove Server")
			}
		}
	}

	if (showConfirmRemoveServer) {
		SimpleConfirm(
			title = "Remove Server",
			message = "Are you sure you want to remove the server?",
			onDismiss = { showConfirmRemoveServer = false },
			onConfirm = {
				scope.launch {
					component.removeServer()
					showConfirmRemoveServer = false
				}
			}
		)
	}

	LaunchedEffect(state.toast) {
		state.toast?.let { snackbarHostState.showSnackbar(it) }
	}

	ServerSetupDialog(component, scope)
}