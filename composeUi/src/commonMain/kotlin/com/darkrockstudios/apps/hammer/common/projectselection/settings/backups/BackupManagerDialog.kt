package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import com.darkrockstudios.apps.hammer.common.compose.actions.MenuOverlay
import com.darkrockstudios.apps.hammer.common.compose.stampDateTime
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogFrame
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogTitleRow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.BackupManager
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationCard
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.lastOf
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef

private val DialogMaxHeight = 720.dp

@Composable
fun BackupManagerDialog(
	component: BackupManager,
	onDismissRequest: () -> Unit,
) {
	val state by component.state.subscribeAsState()
	var pendingRestore by remember { mutableStateOf<ProjectBackupDef?>(null) }
	var pendingDelete by remember { mutableStateOf<ProjectBackupDef?>(null) }
	var isOpen by remember { mutableStateOf(true) }

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = { isOpen = false },
		onClosed = onDismissRequest,
		properties = DialogProperties(
			dismissOnBackPress = true,
			dismissOnClickOutside = false,
			usePlatformDefaultWidth = false,
		),
	) {
		BackupManagerContent(
			state = state,
			onSelectProject = component::selectProject,
			onRestore = { pendingRestore = it },
			onExport = component::exportBackup,
			onDelete = { pendingDelete = it },
			onClose = ::requestDismiss,
			modifier = Modifier.predictiveBackTransform(),
		)
	}

	val restoring = lastOf(pendingRestore)
	AnimatedDialog(
		visible = pendingRestore != null,
		onCloseRequest = { pendingRestore = null },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		restoring?.let { backup ->
			BackupRestoreConfirmCard(
				backup = backup,
				onConfirm = {
					component.restoreBackup(backup)
					pendingRestore = null
				},
				onCancel = { pendingRestore = null },
			)
		}
	}

	val deleting = lastOf(pendingDelete)
	AnimatedDialog(
		visible = pendingDelete != null,
		onCloseRequest = { pendingDelete = null },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		deleting?.let { backup ->
			BackupDeleteConfirmCard(
				backup = backup,
				onConfirm = {
					component.deleteBackup(backup)
					pendingDelete = null
				},
				onCancel = { pendingDelete = null },
			)
		}
	}
}

@Composable
internal fun BackupRestoreConfirmCard(backup: ProjectBackupDef, onConfirm: () -> Unit, onCancel: () -> Unit) {
	ConfirmationCard(
		title = Res.string.backup_restore_dialog_title.get(),
		message = Res.string.backup_restore_dialog_message.get(backup.projectName, backupDateLabel(backup)),
		destructive = true,
		kind = Res.string.confirm_kind_restore.get(),
		confirmLabel = Res.string.backup_restore_dialog_confirm.get(),
		onConfirm = onConfirm,
		cancelLabel = Res.string.backup_restore_dialog_cancel.get(),
		onCancel = onCancel,
	)
}

@Composable
internal fun BackupDeleteConfirmCard(backup: ProjectBackupDef, onConfirm: () -> Unit, onCancel: () -> Unit) {
	ConfirmationCard(
		title = Res.string.backup_delete_dialog_title.get(),
		message = Res.string.backup_delete_dialog_message.get(backupDateLabel(backup)),
		destructive = true,
		kind = Res.string.confirm_kind_delete.get(),
		confirmLabel = Res.string.backup_manager_delete_button.get(),
		onConfirm = onConfirm,
		cancelLabel = Res.string.backup_delete_dialog_cancel.get(),
		onCancel = onCancel,
	)
}

@Composable
private fun backupDateLabel(backup: ProjectBackupDef): String =
	remember(backup.date) { backup.date.stampDateTime() }

@Composable
internal fun BackupManagerContent(
	state: BackupManager.State,
	onSelectProject: (String) -> Unit,
	onRestore: (ProjectBackupDef) -> Unit,
	onExport: (ProjectBackupDef) -> Unit,
	onDelete: (ProjectBackupDef) -> Unit,
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
) {
	HdDialogFrame(
		modifier = Modifier
			.heightIn(max = DialogMaxHeight)
			.fillMaxHeight(0.9f)
			.then(modifier),
	) {
		Column(modifier = Modifier.fillMaxSize()) {
			HdDialogTitleRow(title = Res.string.backup_manager_title.get(), onClose = onClose)

			if (state.availableProjects.isNotEmpty()) {
				ProjectPicker(
					projects = state.availableProjects,
					selected = state.selectedProject,
					onSelect = onSelectProject,
				)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
			) {
				when {
					state.isLoading -> {
						CircularProgressIndicator(
							modifier = Modifier.align(Alignment.Center),
						)
					}

					state.error != null -> ErrorBlock(message = state.error!!)

					state.availableProjects.isEmpty() -> EmptyState(
						text = Res.string.backup_manager_no_backups.get(),
					)

					state.selectedProject != null -> BackupsList(
						backups = state.backupsForSelectedProject,
						onDeleteBackup = onDelete,
						onRestoreBackup = onRestore,
						onExportBackup = onExport,
					)
				}
			}
		}
	}
}

@Composable
private fun ProjectPicker(
	projects: List<String>,
	selected: String?,
	onSelect: (String) -> Unit,
) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.S,
				bottom = Ui.Padding.L,
			),
	) {
		if (projects.size <= 1) {
			Text(
				text = selected ?: Res.string.backup_manager_select_project_hint.get(),
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
		} else {
			HairlinePicker(
				selected = selected ?: Res.string.backup_manager_select_project_hint.get(),
				options = projects,
				onSelect = onSelect,
			)
		}
	}
}

@Composable
private fun HairlinePicker(
	selected: String,
	options: List<String>,
	onSelect: (String) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 36.dp)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.clickable { expanded = true }
				.padding(horizontal = Ui.Padding.L, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = selected,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			HdMonoLabel(
				text = if (expanded) "▲" else "▼",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		MenuOverlay(expanded) { expanded = false }
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			options.forEach { option ->
				DropdownMenuItem(
					text = { Text(option) },
					onClick = {
						onSelect(option)
						expanded = false
					},
				)
			}
		}
	}
}

@Composable
private fun ErrorBlock(message: String) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(Ui.Padding.XL)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.error,
				shape = RectangleShape,
			)
			.padding(Ui.Padding.XL),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HdMonoLabel(
			text = Res.string.backup_manager_error_label.get(),
			color = MaterialTheme.colorScheme.error,
		)
		Text(
			text = message,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun EmptyState(text: String) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(Ui.Padding.XL),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun BackupsList(
	backups: List<ProjectBackupDef>,
	onDeleteBackup: (ProjectBackupDef) -> Unit,
	onRestoreBackup: (ProjectBackupDef) -> Unit,
	onExportBackup: (ProjectBackupDef) -> Unit,
) {
	if (backups.isEmpty()) {
		EmptyState(text = Res.string.backup_manager_no_backups_for_project.get())
		return
	}
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(
			start = Ui.Padding.XL,
			end = Ui.Padding.M,
			top = Ui.Padding.S,
			bottom = Ui.Padding.M,
		),
	) {
		items(backups, key = { it.path.name }) { backup ->
			BackupRow(
				backup = backup,
				onRestore = { onRestoreBackup(backup) },
				onExport = { onExportBackup(backup) },
				onDelete = { onDeleteBackup(backup) },
			)
			HorizontalDivider(
				modifier = Modifier.padding(end = Ui.Padding.XL - Ui.Padding.M),
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}
	}
}

@Composable
private fun BackupRow(
	backup: ProjectBackupDef,
	onRestore: () -> Unit,
	onExport: () -> Unit,
	onDelete: () -> Unit,
) {
	val date = backupDateLabel(backup)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Text(
			text = date,
			style = MaterialTheme.typography.bodyLarge,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		HdHairlineButton(
			label = Res.string.backup_manager_restore_button.get(),
			onClick = onRestore,
		)
		BackupRowMenu(date = date, onExport = onExport, onDelete = onDelete)
	}
}

@Composable
private fun BackupRowMenu(date: String, onExport: () -> Unit, onDelete: () -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	val exportLabel = backupExportLabel()
	Box {
		IconButton(onClick = { expanded = true }) {
			Icon(
				imageVector = Icons.Default.MoreVert,
				contentDescription = Res.string.backup_manager_more.get(date),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		MenuOverlay(expanded) { expanded = false }
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			exportLabel?.let { label ->
				DropdownMenuItem(
					text = { Text(label) },
					onClick = {
						expanded = false
						onExport()
					},
				)
			}
			DropdownMenuItem(
				text = { Text(Res.string.backup_manager_delete_button.get(), color = MaterialTheme.colorScheme.error) },
				onClick = {
					expanded = false
					onDelete()
				},
			)
		}
	}
}
