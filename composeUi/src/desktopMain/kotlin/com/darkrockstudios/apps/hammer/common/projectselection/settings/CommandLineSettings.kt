package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.rememberClipboardCopier
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPath
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPathInstaller
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPathState
import com.darkrockstudios.apps.hammer.common.compose.plugin.cliLauncher
import com.darkrockstudios.apps.hammer.common.compose.plugin.shellQuoted
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.hostOs
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_absent
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_add
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_admin
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_failed
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_installed
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_close
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_copied
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_copy
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_full_command
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_full_command_info
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_full_command_show
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_manual
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_new_terminals
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_off_path
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_provided
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_remove
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_stale
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_taken
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_title
import com.darkrockstudios.apps.hammer.composeui.resources.cli_path_update
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where `hammer` comes from on this install, and a button to put it on PATH where Hammer can. */
@Composable
internal fun CommandLineSettings(
	cliPath: CliPath = remember { CliPath.detect() },
	installer: CliPathInstaller = remember { CliPathInstaller() },
	launcher: List<String> = remember { cliLauncher() },
	// The user's shell PATH, where the app's own is a fair guess: not inside Flatpak's sandbox, not on
	// macOS, where apps get launchd's PATH without the /usr/local/bin every shell has, and not on
	// Windows, where Hammer adds the folder to PATH itself.
	path: String? = remember {
		System.getenv("PATH").takeIf { System.getenv("FLATPAK_ID") == null && hostOs == HostOs.Linux }
	},
) {
	if (cliPath == CliPath.Unavailable) return
	var showFullCommand by remember { mutableStateOf(false) }
	Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
			HdMonoLabel(text = Res.string.cli_path_title.get())
			IconButton(onClick = { showFullCommand = true }, modifier = Modifier.size(28.dp)) {
				Icon(
					Icons.Outlined.Info,
					contentDescription = Res.string.cli_path_full_command_show.get(),
					modifier = Modifier.size(18.dp),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		when (cliPath) {
			is CliPath.Provided -> Text(Res.string.cli_path_provided.get(cliPath.command), style = MaterialTheme.typography.bodyMedium)
			is CliPath.Manual -> {
				Text(Res.string.cli_path_manual.get(), style = MaterialTheme.typography.bodyMedium)
				Monospace(cliPath.command)
			}
			is CliPath.Script -> ScriptControls(cliPath, installer, path)
			CliPath.Unavailable -> Unit
		}
	}
	FullCommandDialog(
		command = launcher.joinToString(" ", transform = ::shellQuoted),
		visible = showFullCommand,
		onDismiss = { showFullCommand = false },
	)
}

@Composable
private fun FullCommandDialog(command: String, visible: Boolean, onDismiss: () -> Unit) {
	val copy = rememberClipboardCopier()
	var copied by remember(visible) { mutableStateOf(false) }
	AnimatedDialog(visible = visible, onCloseRequest = onDismiss) {
		HdHairlineDialogShell(
			title = Res.string.cli_path_full_command.get(),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.cli_path_close.get(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(Res.string.cli_path_full_command_info.get(), style = MaterialTheme.typography.bodyMedium)
				Monospace(command)
				HdHairlineButton(
					label = (if (copied) Res.string.cli_path_copied else Res.string.cli_path_copy).get(),
					onClick = {
						copy(command)
						copied = true
					},
				)
			}
		}
	}
}

@Composable
private fun ScriptControls(script: CliPath.Script, installer: CliPathInstaller, path: String?) {
	val scope = rememberCoroutineScope()
	var state by remember { mutableStateOf<CliPathState?>(null) }
	var working by remember { mutableStateOf(false) }
	var failure by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(script) { state = withContext(Dispatchers.IO) { installer.state(script) } }

	fun change(action: (CliPath.Script) -> Unit) {
		working = true
		failure = null
		scope.launch {
			try {
				withContext(Dispatchers.IO) { action(script) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: IOException) {
				failure = e.message ?: e.toString()
			}
			state = withContext(Dispatchers.IO) { installer.state(script) }
			working = false
		}
	}

	val file = script.file.toString()
	val current = state ?: return
	val message = when (current) {
		CliPathState.Absent -> Res.string.cli_path_absent.get(script.file.parent.toString())
		CliPathState.Installed -> Res.string.cli_path_installed.get(file)
		CliPathState.Stale -> Res.string.cli_path_stale.get(file)
		CliPathState.Taken -> Res.string.cli_path_taken.get(file)
	}
	Text(message, style = MaterialTheme.typography.bodyMedium)
	if (current == CliPathState.Installed && path != null && !script.onPath(path)) {
		Text(Res.string.cli_path_off_path.get(script.file.parent.toString()), style = MaterialTheme.typography.bodyMedium)
	}
	if (current == CliPathState.Installed && script.windows) {
		Text(Res.string.cli_path_new_terminals.get(), style = MaterialTheme.typography.bodySmall)
	}
	if (current == CliPathState.Absent && script.needsAdmin) {
		Text(Res.string.cli_path_admin.get(), style = MaterialTheme.typography.bodySmall)
	}
	when (current) {
		CliPathState.Absent -> HdHairlineButton(Res.string.cli_path_add.get(), onClick = { change(installer::install) }, enabled = !working)
		CliPathState.Stale -> HdHairlineButton(Res.string.cli_path_update.get(), onClick = { change(installer::install) }, enabled = !working)
		CliPathState.Installed -> HdHairlineButton(Res.string.cli_path_remove.get(), onClick = { change(installer::remove) }, enabled = !working)
		CliPathState.Taken -> Unit
	}
	failure?.let { Text(Res.string.cli_path_failed.get(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Monospace(text: String) {
	SelectionContainer {
		Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
	}
}
