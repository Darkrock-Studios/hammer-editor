package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.BackupManager
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.util.formatLocal

@Composable
fun BackupManagerDialog(
	component: BackupManager,
	onDismissRequest: () -> Unit,
) {
	val state by component.state.subscribeAsState()
	val showRestoreConfirm = remember { mutableStateOf<ProjectBackupDef?>(null) }

	Dialog(
		onDismissRequest = onDismissRequest,
		properties = DialogProperties(
			dismissOnBackPress = true,
			dismissOnClickOutside = false,
			usePlatformDefaultWidth = false
		)
	) {
		Surface(
			modifier = Modifier
				.widthIn(max = 480.dp)
				.fillMaxWidth()
				.fillMaxHeight(0.8f),
			shape = RoundedCornerShape(8.dp),
			color = MaterialTheme.colorScheme.background
		) {
			Column(
				modifier = Modifier.fillMaxSize()
			) {
				// Header
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = Res.string.backup_manager_title.get(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onBackground
					)

					IconButton(onClick = onDismissRequest) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = Res.string.backup_manager_close_content_description.get(),
							tint = MaterialTheme.colorScheme.onBackground
						)
					}
				}

				// Project selector dropdown
				if (state.availableProjects.isNotEmpty()) {
					ProjectSelector(
						projects = state.availableProjects,
						selectedProject = state.selectedProject,
						onProjectSelected = component::selectProject,
						modifier = Modifier.padding(horizontal = Ui.Padding.XL)
					)
				}

				// Content area
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f)
						.padding(horizontal = Ui.Padding.XL)
				) {
					when {
						state.isLoading -> {
							CircularProgressIndicator(
								modifier = Modifier.align(Alignment.Center)
							)
						}

						state.error != null -> {
							Card(
								modifier = Modifier.fillMaxWidth(),
								colors = CardDefaults.cardColors(
									containerColor = MaterialTheme.colorScheme.errorContainer
								)
							) {
								Text(
									text = state.error!!,
									color = MaterialTheme.colorScheme.onErrorContainer,
									modifier = Modifier.padding(Ui.Padding.XL)
								)
							}
						}

						state.availableProjects.isEmpty() -> {
							Text(
								text = Res.string.backup_manager_no_backups.get(),
								style = MaterialTheme.typography.bodyLarge,
								modifier = Modifier.align(Alignment.Center)
							)
						}

						state.selectedProject != null -> {
							BackupsList(
								backups = state.backupsForSelectedProject,
								onDeleteBackup = component::deleteBackup,
								onRestoreBackup = { backup -> showRestoreConfirm.value = backup }
							)
						}
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
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSelector(
	projects: List<String>,
	selectedProject: String?,
	onProjectSelected: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	var expanded by remember { mutableStateOf(false) }

	ExposedDropdownMenuBox(
		expanded = expanded,
		onExpandedChange = { expanded = it },
		modifier = modifier
	) {
		OutlinedTextField(
			value = selectedProject ?: Res.string.backup_manager_select_project_hint.get(),
			onValueChange = {},
			readOnly = true,
			label = { Text(Res.string.backup_manager_project_label.get()) },
			trailingIcon = {
				ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
			},
			modifier = Modifier
				.menuAnchor()
				.fillMaxWidth()
		)

		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			projects.forEach { project ->
				DropdownMenuItem(
					text = { Text(project) },
					onClick = {
						onProjectSelected(project)
						expanded = false
					}
				)
			}
		}
	}
}

@Composable
private fun BackupsList(
	backups: List<ProjectBackupDef>,
	onDeleteBackup: (ProjectBackupDef) -> Unit,
	onRestoreBackup: (ProjectBackupDef) -> Unit
) {
	if (backups.isEmpty()) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			Text(
				text = Res.string.backup_manager_no_backups_for_project.get(),
				style = MaterialTheme.typography.bodyLarge
			)
		}
	} else {
		LazyColumn(
			verticalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M)
		) {
			items(backups) { backup ->
				BackupItem(
					backup = backup,
					onDelete = { onDeleteBackup(backup) },
					onRestore = { onRestoreBackup(backup) }
				)
			}
		}
	}
}

@Composable
private fun BackupItem(
	backup: ProjectBackupDef,
	onDelete: () -> Unit,
	onRestore: () -> Unit
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(
				modifier = Modifier.weight(1f)
			) {
				val formattedDate = remember(backup.date) { backup.date.formatLocal("MMM dd, yyyy HH:mm") }
				Text(
					text = formattedDate,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium
				)

				Text(
					text = backup.path.name,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}

			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				TextButton(onClick = onRestore) {
					Text(Res.string.backup_manager_restore_button.get())
				}

				IconButton(onClick = onDelete) {
					Icon(
						imageVector = Icons.Default.Delete,
						contentDescription = Res.string.backup_manager_delete_content_description.get(),
						tint = MaterialTheme.colorScheme.error
					)
				}
			}
		}
	}
}