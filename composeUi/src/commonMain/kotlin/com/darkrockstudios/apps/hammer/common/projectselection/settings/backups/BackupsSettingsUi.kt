package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BackupsSettingsUi(
	component: AccountSettings,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val backupManagerSlot by component.backupManagerSlot.subscribeAsState()

	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Text(
			text = Res.string.settings_backups_explainations.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)

		var maxBackupsValue by remember(state.maxBackups) { mutableStateOf("${state.maxBackups}") }
		val parsedMaxBackups = maxBackupsValue.toIntOrNull()
		val isMaxBackupsError = parsedMaxBackups == null
				|| parsedMaxBackups !in 1..GlobalSettings.MAX_BACKUPS

		HdHairlineField(
			modifier = Modifier.widthIn(max = 320.dp),
			label = Res.string.settings_server_max_backups.get(),
			value = maxBackupsValue,
			onValueChange = { input ->
				val digits = input.filter(Char::isDigit)
				maxBackupsValue = digits
				digits.toIntOrNull()?.let { value ->
					if (value in 1..GlobalSettings.MAX_BACKUPS) {
						scope.launch { component.setMaxBackups(value) }
					}
				}
			},
			hint = "1–${GlobalSettings.MAX_BACKUPS}",
			imeAction = ImeAction.Done,
			error = if (isMaxBackupsError) {
				Res.string.settings_server_max_backups_error.get(GlobalSettings.MAX_BACKUPS)
			} else {
				null
			},
		)

		HdHairlineButton(
			label = Res.string.settings_backups_manage_button.get(),
			onClick = { component.showBackupManager() },
		)
	}

	backupManagerSlot.child?.instance?.let { backupManager ->
		BackupManagerDialog(
			component = backupManager,
			onDismissRequest = {
				component.dismissBackupManager()
			},
		)
	}
}
