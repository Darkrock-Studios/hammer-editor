package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.util.formatLocal

private val DialogMaxWidth = 540.dp
private val DialogMaxHeight = 720.dp

@Composable
fun BackupManagerDialog(
	component: BackupManager,
	onDismissRequest: () -> Unit,
) {
	val state by component.state.subscribeAsState()
	val showRestoreConfirm = remember { mutableStateOf<ProjectBackupDef?>(null) }
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
		Surface(
			modifier = Modifier
				.padding(Ui.Padding.XL)
				.widthIn(max = DialogMaxWidth)
				.heightIn(max = DialogMaxHeight)
				.fillMaxWidth()
				.fillMaxHeight(0.9f)
				.predictiveBackTransform(),
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			border = BorderStroke(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			),
		) {
			Column(modifier = Modifier.fillMaxSize()) {
				Masthead(
					backupCount = state.backupsForSelectedProject.size,
					onClose = ::requestDismiss,
				)
				HdFolioDivider()

				Text(
					text = Res.string.backup_manager_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							start = Ui.Padding.XL,
							end = Ui.Padding.XL,
							top = Ui.Padding.L,
							bottom = Ui.Padding.S,
						),
				)

				if (state.availableProjects.isNotEmpty()) {
					ProjectPicker(
						projects = state.availableProjects,
						selected = state.selectedProject,
						onSelect = component::selectProject,
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
							onDeleteBackup = component::deleteBackup,
							onRestoreBackup = { backup -> showRestoreConfirm.value = backup },
							onExportBackup = component::exportBackup,
						)
					}
				}
			}
		}
	}

	showRestoreConfirm.value?.let { backup ->
		SimpleConfirm(
			title = Res.string.backup_restore_dialog_title.get(),
			message = Res.string.backup_restore_dialog_message.get(),
			positiveButton = Res.string.backup_restore_dialog_confirm.get(),
			negativeButton = Res.string.backup_restore_dialog_cancel.get(),
			implicitCancel = true,
			onDismiss = { showRestoreConfirm.value = null },
			onConfirm = {
				component.restoreBackup(backup)
				showRestoreConfirm.value = null
			},
		)
	}
}

@Composable
private fun Masthead(
	backupCount: Int,
	onClose: () -> Unit,
) {
	HdMasthead(
		section = "BACKUPS",
		leadingMeta = if (backupCount > 0) listOf("§§ $backupCount") else emptyList(),
		trailing = { HdMastheadAction(label = "× CLOSE", onClick = onClose) },
	)
}

@Composable
private fun ProjectPicker(
	projects: List<String>,
	selected: String?,
	onSelect: (String) -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.M,
				bottom = Ui.Padding.L,
			),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HdMonoLabel(text = "PROJECT")

		if (projects.size <= 1) {
			Text(
				text = selected ?: Res.string.backup_manager_select_project_hint.get(),
				style = MaterialTheme.typography.bodyMedium,
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
			text = "ERROR",
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
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			HdMonoLabel(text = "NO BACKUPS")
			Text(
				text = text,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
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
			horizontal = Ui.Padding.XL,
			vertical = Ui.Padding.M,
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
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupRow(
	backup: ProjectBackupDef,
	onRestore: () -> Unit,
	onExport: () -> Unit,
	onDelete: () -> Unit,
) {
	val greebleId = remember(backup.date) { "BAK-" + backup.date.formatLocal("yyyyMMdd-HHmm") }
	val humanDate = remember(backup.date) { backup.date.formatLocal("dd MMM · HH:mm") }

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = Ui.Padding.L),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			HdMonoLabel(
				text = greebleId,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.weight(1f))
			HdMonoLabel(text = humanDate)
		}

		Text(
			text = backup.path.name,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		FlowRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			HdHairlineButton(
				label = Res.string.backup_manager_restore_button.get(),
				onClick = onRestore,
				emphasised = true,
			)
			BackupExportAction(onExport = onExport)
			HdHairlineButton(
				label = Res.string.backup_manager_delete_content_description.get(),
				onClick = onDelete,
				danger = true,
			)
		}
	}
}

