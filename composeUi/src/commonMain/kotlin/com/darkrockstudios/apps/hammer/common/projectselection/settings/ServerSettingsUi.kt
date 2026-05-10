package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCatalogueCard
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val EMAIL_GREEBLE_MAX = 28

@Composable
fun ServerSettingsUi(
	component: AccountSettings,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	var showConfirmRemoveServer by rememberSaveable { mutableStateOf(false) }
	var showHelpDialog by rememberSaveable { mutableStateOf(false) }

	val topEnd = if (state.serverIsLoggedIn) {
		val email = state.currentEmail
		if (!email.isNullOrBlank()) {
			"CONNECTED · ${truncate(email, EMAIL_GREEBLE_MAX)}"
		} else {
			"CONNECTED"
		}
	} else {
		"NOT CONNECTED"
	}
	val bottomStart = state.currentUrl
		?.takeIf { state.serverIsLoggedIn && it.isNotBlank() }
		?.let { "URL · ${truncate(it, EMAIL_GREEBLE_MAX)}" }

	HdCatalogueCard(
		modifier = modifier,
		topStart = "§ III · SYNC",
		topEnd = topEnd,
		bottomStart = bottomStart,
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = Res.string.settings_server_description.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
			)
			Spacer(Modifier.size(12.dp))
			HdMonoLabel(
				text = "HELP ↗",
				modifier = Modifier.clickable { showHelpDialog = true },
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}

		Spacer(Modifier.size(20.dp))

		AnimatedContent(targetState = state.serverIsLoggedIn) { loggedIn ->
			if (loggedIn) {
				LoggedInBody(
					autoSync = state.syncAutomaticSync,
					autoCloseDialog = state.syncAutoCloseDialog,
					autoBackups = state.syncAutomaticBackups,
					onAutoSyncChange = { scope.launch { component.setAutoSyncing(it) } },
					onAutoCloseChange = { scope.launch { component.setAutoCloseDialogs(it) } },
					onAutoBackupsChange = { scope.launch { component.setAutomaticBackups(it) } },
					onRemoveServer = { showConfirmRemoveServer = true },
				)
			} else {
				LoggedOutBody(
					onSetupServer = { scope.launch { component.beginSetupServer() } },
				)
			}
		}
	}

	if (showConfirmRemoveServer) {
		SimpleConfirm(
			title = Res.string.settings_remove_server_dialog_title.get(),
			message = Res.string.settings_remove_server_dialog_message.get(),
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

	if (showHelpDialog) {
		ServerSetupHelpDialog(onDismiss = { showHelpDialog = false })
	}
}

@Composable
private fun LoggedOutBody(
	onSetupServer: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		Text(
			text = Res.string.settings_server_enable_sync_backup_desc.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HdHairlineButton(
			label = Res.string.settings_server_setup_button.get(),
			onClick = onSetupServer,
			emphasised = true,
		)
	}
}

@Composable
private fun LoggedInBody(
	autoSync: Boolean,
	autoCloseDialog: Boolean,
	autoBackups: Boolean,
	onAutoSyncChange: (Boolean) -> Unit,
	onAutoCloseChange: (Boolean) -> Unit,
	onAutoBackupsChange: (Boolean) -> Unit,
	onRemoveServer: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		HdMonoLabel(text = Res.string.settings_server_sync_backup_preferences.get())

		HdHairlineToggleRow(
			checked = autoSync,
			onCheckedChange = onAutoSyncChange,
			label = Res.string.settings_server_auto_sync.get(),
		)
		HdHairlineToggleRow(
			checked = autoCloseDialog,
			onCheckedChange = onAutoCloseChange,
			label = Res.string.settings_server_sync_auto_close.get(),
		)
		HdHairlineToggleRow(
			checked = autoBackups,
			onCheckedChange = onAutoBackupsChange,
			label = Res.string.settings_server_sync_backup.get(),
		)

		Spacer(Modifier.size(8.dp))
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		Spacer(Modifier.size(4.dp))

		HdMonoLabel(
			text = Res.string.settings_server_danger_zone.get(),
			color = MaterialTheme.colorScheme.error,
		)
		HdHairlineButton(
			label = Res.string.settings_server_remove_server_button.get(),
			onClick = onRemoveServer,
			danger = true,
		)
	}
}

private fun truncate(text: String, max: Int): String =
	if (text.length <= max) text else text.take(max - 1) + "…"
