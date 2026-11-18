package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdToolButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMetaStyle
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.settings_backups_decrease
import com.darkrockstudios.apps.hammer.settings_backups_increase
import com.darkrockstudios.apps.hammer.settings_group_backups
import com.darkrockstudios.apps.hammer.settings_row_manage_backups
import com.darkrockstudios.apps.hammer.settings_row_manage_backups_button
import com.darkrockstudios.apps.hammer.settings_row_max_backups
import com.darkrockstudios.apps.hammer.settings_row_max_backups_desc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Text

@Composable
fun BackupsSettingsUi(
	component: AccountSettings,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val backupManagerSlot by component.backupManagerSlot.subscribeAsState()

	HdSettingGroup(title = Res.string.settings_group_backups.get(), modifier = modifier) {
		HdSettingRow(
			title = Res.string.settings_row_max_backups.get(),
			description = Res.string.settings_row_max_backups_desc.get(),
		) {
			BackupCountStepper(
				value = state.maxBackups,
				onChange = { scope.launch { component.setMaxBackups(it) } },
			)
		}
		HdSettingRow(title = Res.string.settings_row_manage_backups.get()) {
			HdHairlineButton(
				label = Res.string.settings_row_manage_backups_button.get(),
				onClick = { component.showBackupManager() },
			)
		}
	}

	backupManagerSlot.child?.instance?.let { backupManager ->
		BackupManagerDialog(
			component = backupManager,
			onDismissRequest = { component.dismissBackupManager() },
		)
	}
}

/** − n +, clamped to 1..[GlobalSettings.MAX_BACKUPS], so there is no invalid value to type. */
@Composable
private fun BackupCountStepper(value: Int, onChange: (Int) -> Unit) {
	val range = 1..GlobalSettings.MAX_BACKUPS
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		HdToolButton(active = false, onClick = { if (value - 1 in range) onChange(value - 1) }) {
			Icon(
				Icons.Filled.Remove,
				contentDescription = Res.string.settings_backups_decrease.get(),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(16.dp),
			)
		}
		Text(
			text = value.toString(),
			style = hdMetaStyle(),
			color = MaterialTheme.colorScheme.onSurface,
			textAlign = TextAlign.Center,
			modifier = Modifier.widthIn(min = 32.dp),
		)
		HdToolButton(active = false, onClick = { if (value + 1 in range) onChange(value + 1) }) {
			Icon(
				Icons.Filled.Add,
				contentDescription = Res.string.settings_backups_increase.get(),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(16.dp),
			)
		}
	}
}
