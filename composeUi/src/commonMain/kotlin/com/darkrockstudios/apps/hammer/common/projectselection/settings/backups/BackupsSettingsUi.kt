package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BackupsSettingsUi(
	component: AccountSettings,
	scope: CoroutineScope,
) {
	val state by component.state.subscribeAsState()
	val backupManagerSlot by component.backupManagerSlot.subscribeAsState()

	Column(
		modifier = Modifier.padding(Ui.Padding.M)
	) {
		Text(
			Res.string.settings_backups_header.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Text(
			Res.string.settings_backups_description.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onBackground,
			fontStyle = FontStyle.Italic
		)

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		Text(
			Res.string.settings_backups_explainations.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onBackground,
			fontStyle = FontStyle.Italic
		)

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		var maxBackupsValue by remember(state.maxBackups) { mutableStateOf("${state.maxBackups}") }
		val isMaxBackupsError = remember(maxBackupsValue) {
			val value = maxBackupsValue.toIntOrNull()
			value == null || value !in 1..GlobalSettings.MAX_BACKUPS
		}

		OutlinedTextField(
			modifier = Modifier.width(200.dp),
			value = maxBackupsValue,
			onValueChange = { newText ->
				if (newText.isEmpty() || newText.all { it.isDigit() }) {
					maxBackupsValue = newText
					newText.toIntOrNull()?.let { value ->
						if (value in 1..GlobalSettings.MAX_BACKUPS) {
							scope.launch { component.setMaxBackups(value) }
						}
					}
				}
			},
			label = { Text(Res.string.settings_server_max_backups.get()) },
			singleLine = true,
			isError = isMaxBackupsError,
			supportingText = {
				if (isMaxBackupsError) {
					Text(Res.string.settings_server_max_backups_error.get(GlobalSettings.MAX_BACKUPS))
				}
			}
		)

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		Button(onClick = {
			component.showBackupManager()
		}) {
			Text(Res.string.settings_backups_manage_button.get())
		}
	}

	backupManagerSlot.child?.instance?.let { backupManager ->
		BackupManagerDialog(
			component = backupManager,
			onDismissRequest = {
				component.dismissBackupManager()
			}
		)
	}
}
