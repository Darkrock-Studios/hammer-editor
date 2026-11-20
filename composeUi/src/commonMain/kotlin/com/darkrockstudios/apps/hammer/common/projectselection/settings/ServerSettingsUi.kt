package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMetaStyle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdQuietButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdControlFrame
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_group_sync_options
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_cancel
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_confirm
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_message
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_title
import com.darkrockstudios.apps.hammer.settings_row_remove_server
import com.darkrockstudios.apps.hammer.settings_row_remove_server_button
import com.darkrockstudios.apps.hammer.settings_row_remove_server_desc
import com.darkrockstudios.apps.hammer.settings_server_auto_sync
import com.darkrockstudios.apps.hammer.settings_server_enable_sync_backup_desc
import com.darkrockstudios.apps.hammer.settings_server_log_in_again_button
import com.darkrockstudios.apps.hammer.settings_server_setup_button
import com.darkrockstudios.apps.hammer.settings_server_signed_out_message
import com.darkrockstudios.apps.hammer.settings_server_sync_auto_close
import com.darkrockstudios.apps.hammer.settings_server_sync_backup
import com.darkrockstudios.apps.hammer.settings_sync_help
import com.darkrockstudios.apps.hammer.settings_sync_status_connected
import com.darkrockstudios.apps.hammer.settings_sync_status_connected_as
import com.darkrockstudios.apps.hammer.settings_sync_status_not_set_up
import com.darkrockstudios.apps.hammer.settings_sync_status_signed_out
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.darkrockstudios.apps.hammer.confirm_kind_remove

/**
 * The top of the Sync tab: one framed status block (connected, signed out, or not set up) with
 * the action that state needs, then the sync options once a server is configured.
 */
@Composable
internal fun SyncSettingsContent(component: AccountSettings, scope: CoroutineScope) {
	val state by component.state.subscribeAsState()
	var showHelpDialog by rememberSaveable { mutableStateOf(false) }
	val host = state.currentUrl?.let(::hostOf)?.takeIf { it.isNotBlank() }

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.hdControlFrame(MaterialTheme.colorScheme.outlineVariant)
			.padding(Ui.Padding.XL),
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		val connected = state.serverIsConfigured && state.serverIsLoggedIn
		val status = when {
			connected && !state.currentEmail.isNullOrBlank() && host != null ->
				Res.string.settings_sync_status_connected_as.get(host, state.currentEmail!!)

			connected && host != null -> Res.string.settings_sync_status_connected.get(host)
			state.serverIsConfigured && host != null -> Res.string.settings_sync_status_signed_out.get(host)
			else -> Res.string.settings_sync_status_not_set_up.get()
		}
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Icon(
				imageVector = if (connected) Icons.Outlined.Cloud else Icons.Outlined.CloudOff,
				contentDescription = null,
				tint = if (connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp),
			)
			Text(
				text = styledStatus(status, host, state.currentUrl, state.currentEmail),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
			)
		}
		when {
			!state.serverIsConfigured -> {
				Text(
					text = Res.string.settings_server_enable_sync_backup_desc.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				HdHairlineButton(
					label = Res.string.settings_server_setup_button.get(),
					onClick = { scope.launch { component.beginSetupServer() } },
					emphasised = true,
				)
			}

			!state.serverIsLoggedIn -> {
				Text(
					text = Res.string.settings_server_signed_out_message.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				HdHairlineButton(
					label = Res.string.settings_server_log_in_again_button.get(),
					onClick = { component.reauthenticate() },
					emphasised = true,
				)
			}
		}
		HdQuietButton(
			onClick = { showHelpDialog = true },
			icon = Icons.AutoMirrored.Outlined.HelpOutline,
			label = Res.string.settings_sync_help.get(),
		)
	}

	if (state.serverIsConfigured) {
		HdSettingGroup(title = Res.string.settings_group_sync_options.get()) {
			HdHairlineToggleRow(
				checked = state.syncAutomaticSync,
				onCheckedChange = { scope.launch { component.setAutoSyncing(it) } },
				label = Res.string.settings_server_auto_sync.get(),
			)
			HdHairlineToggleRow(
				checked = state.syncAutoCloseDialog,
				onCheckedChange = { scope.launch { component.setAutoCloseDialogs(it) } },
				label = Res.string.settings_server_sync_auto_close.get(),
			)
			HdHairlineToggleRow(
				checked = state.syncAutomaticBackups,
				onCheckedChange = { scope.launch { component.setAutomaticBackups(it) } },
				label = Res.string.settings_server_sync_backup.get(),
			)
		}
	}

	if (showHelpDialog) {
		ServerSetupHelpDialog(onDismiss = { showHelpDialog = false })
	}
}

/** Last on the Sync tab, alone, in the error colour; nothing when no server is configured. */
@Composable
internal fun RemoveServerRow(component: AccountSettings, scope: CoroutineScope) {
	val state by component.state.subscribeAsState()
	if (!state.serverIsConfigured) return
	var confirm by rememberSaveable { mutableStateOf(false) }

	HdSettingGroup(title = null) {
		HdSettingRow(
			title = Res.string.settings_row_remove_server.get(),
			description = Res.string.settings_row_remove_server_desc.get(),
			titleColor = MaterialTheme.colorScheme.error,
		) {
			HdHairlineButton(
				label = Res.string.settings_row_remove_server_button.get(),
				onClick = { confirm = true },
				danger = true,
			)
		}
	}

	if (confirm) {
		SimpleConfirm(
			title = Res.string.settings_remove_server_dialog_title.get(),
			message = Res.string.settings_remove_server_dialog_message.get(),
			positiveButton = Res.string.settings_remove_server_dialog_confirm.get(),
			negativeButton = Res.string.settings_remove_server_dialog_cancel.get(),
			destructive = true,
			kind = Res.string.confirm_kind_remove.get(),
			onDismiss = { confirm = false },
			onConfirm = {
				scope.launch {
					component.removeServer()
					confirm = false
				}
			},
		)
	}
}

/**
 * Sets the host and email in the status line apart as identifiers (mono). The host links to the
 * server, which serves its own pages; the email isn't a link, so it doesn't look like one.
 */
@Composable
private fun styledStatus(status: String, host: String?, url: String?, email: String?): AnnotatedString {
	val mono = hdMetaStyle().fontFamily
	val primary = MaterialTheme.colorScheme.primary
	return buildAnnotatedString {
		append(status)
		if (host != null && url != null) {
			val start = status.indexOf(host)
			if (start >= 0) {
				addStyle(SpanStyle(fontFamily = mono, color = primary, textDecoration = TextDecoration.Underline), start, start + host.length)
				addLink(LinkAnnotation.Url(url), start, start + host.length)
			}
		}
		if (!email.isNullOrBlank()) {
			val start = status.lastIndexOf(email)
			if (start >= 0) addStyle(SpanStyle(fontFamily = mono), start, start + email.length)
		}
	}
}

private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/')
