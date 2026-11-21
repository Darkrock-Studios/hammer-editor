package com.darkrockstudios.apps.hammer.common.projectsync

import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogFrame
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogTitleRow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import com.darkrockstudios.apps.hammer.common.compose.WindowSizeClass
import com.darkrockstudios.apps.hammer.common.compose.rememberWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.toCompactText
import com.darkrockstudios.apps.hammer.common.projectselection.SyncLogList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DialogMaxWidth = 800.dp

/** Wide enough for two side-by-side prose panes at a comfortable line length. */
private val ConflictDialogMaxWidth = 1280.dp
private val DialogBodyMinHeight = 280.dp

@Composable
internal fun ProjectSynchronization(
	component: ProjectSynchronization,
	showSnackbar: (String) -> Unit
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	var isOpen by remember { mutableStateOf(true) }
	var confirmCancel by rememberSaveable { mutableStateOf(false) }
	val syncCanceledText = Res.string.account_sync_toast_canceled.get()

	val requestClose = {
		if (state.isSyncing.not()) isOpen = false else confirmCancel = true
	}

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = requestClose,
		onClosed = { component.endSync() },
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		BoxWithConstraints {
			ProjectSynchronizationContent(
				component = component,
				showSnackbar = showSnackbar,
				screenCharacteristics = rememberWindowSizeClass(constraints),
				modifier = Modifier.predictiveBackTransform(),
				onClose = requestClose,
			)
		}
	}

	if (confirmCancel) {
		SimpleConfirm(
			title = Res.string.account_sync_confirm_cancel_title.get(),
			message = Res.string.account_sync_confirm_cancel_message.get(),
			positiveButton = Res.string.account_sync_confirm_cancel_stop.get(),
			negativeButton = Res.string.account_sync_confirm_cancel_keep.get(),
			onDismiss = { confirmCancel = false },
			onConfirm = {
				component.cancelSync()
				scope.launch {
					withContext(mainDispatcher) {
						confirmCancel = false
						isOpen = false
					}
				}
				showSnackbar(syncCanceledText)
			},
		)
	}
}

@Composable
internal fun ProjectSynchronizationContent(
	component: ProjectSynchronization,
	showSnackbar: (String) -> Unit,
	screenCharacteristics: WindowSizeClass,
	modifier: Modifier = Modifier,
	onClose: () -> Unit = {},
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val snackbarHostState = remember { SnackbarHostState() }

	val showingConflict = state.entityConflict != null || state.projectDataConflict != null
	val maxWidth by animateDpAsState(if (showingConflict) ConflictDialogMaxWidth else DialogMaxWidth)

	LaunchedEffect(Unit) {
		component.syncProject { success ->
			scope.launch {
				if (success) {
					showSnackbar(strRes.get(Res.string.sync_toast_success))
				} else {
					showSnackbar(strRes.get(Res.string.sync_toast_failed))
				}
			}
		}
	}

	HdDialogFrame(modifier = modifier, maxWidth = maxWidth) {
		Column {
			TitleAndToolbar(
				inLogView = state.showLog,
				isSyncing = state.isSyncing,
				onStop = { component.cancelSync() },
				onToggleLog = { component.showLog(!state.showLog) },
				onClose = onClose,
			)

			if (!state.showLog) {
				OverallProgressStrip(state)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = DialogBodyMinHeight),
			) {
				val projectDataConflict = state.projectDataConflict
				val conflict = state.entityConflict
				when {
					projectDataConflict != null || conflict != null -> ConflictBody(
						summary = rememberConflictSummary(conflict, projectDataConflict),
						infoMessage = if (conflict != null) Res.string.sync_conflict_merge_explained.get() else null,
						onInfoClick = { msg ->
							scope.launch {
								snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
							}
						},
					) {
						if (projectDataConflict != null) {
							ProjectDataConflict(projectDataConflict, component, screenCharacteristics)
						} else when (conflict!!) {
							is ProjectSynchronization.EntityConflict.SceneConflict ->
								SceneConflict(conflict, component, screenCharacteristics)

							is ProjectSynchronization.EntityConflict.NoteConflict ->
								NoteConflict(conflict, component, screenCharacteristics)

							is ProjectSynchronization.EntityConflict.TimelineEventConflict ->
								TimelineEventConflict(conflict, component, screenCharacteristics)

							is ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict ->
								EncyclopediaEntryConflict(conflict, component, screenCharacteristics)

							is ProjectSynchronization.EntityConflict.SceneDraftConflict ->
								SceneDraftConflict(conflict, component, screenCharacteristics)

							is ProjectSynchronization.EntityConflict.ImageConflict ->
								ImageConflict(conflict, component, screenCharacteristics)
						}
					}

					state.showLog -> SyncLogList(state.syncLog, showProjectName = false, horizontalPadding = Ui.Padding.XL)

					else -> StatusBody(state)
				}

				SnackbarHost(
					hostState = snackbarHostState,
					modifier = Modifier.align(Alignment.BottomCenter),
				)
			}

		}
	}
}

@Composable
private fun TitleAndToolbar(
	inLogView: Boolean,
	isSyncing: Boolean,
	onStop: () -> Unit,
	onToggleLog: () -> Unit,
	onClose: () -> Unit,
) {
	HdDialogTitleRow(
		title = if (inLogView) {
			Res.string.account_sync_log_title.get()
		} else {
			Res.string.sync_project_dialog_title.get()
		},
		onClose = onClose,
	) {
		if (isSyncing) {
			HdToolButton(active = false, onClick = onStop) {
				HdStopGlyph()
			}
		}
		HdToolButton(active = inLogView, onClick = onToggleLog) {
			HdLogGlyph()
		}
	}
}

@Composable
private fun OverallProgressStrip(state: ProjectSynchronization.State) {
	val status = when {
		state.failed -> HdStatus.Failed
		state.isSyncing -> HdStatus.Syncing
		else -> HdStatus.Complete
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdHairlineProgressBar(
			progress = state.syncProgress,
			color = status.accentColor(),
			modifier = Modifier.weight(1f),
		)
		HdMonoLabel(
			text = Res.string.sync_project_dialog_percent.get((state.syncProgress * 100).toInt()),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun BoxScope.StatusBody(state: ProjectSynchronization.State) {
	val (status, label) = when {
		state.failed -> HdStatus.Failed to Res.string.sync_status_failed.get()
		state.isSyncing -> HdStatus.Syncing to Res.string.sync_status_in_progress.get()
		else -> HdStatus.Complete to Res.string.sync_status_success.get()
	}
	Column(
		modifier = Modifier
			.align(Alignment.Center)
			.padding(Ui.Padding.XL),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdStatusGlyph(status, size = 28.dp)
		HdMonoLabel(text = label)
	}
}

internal data class ConflictSummary(
	val label: String,
	val name: String?,
	val meta: String?,
)

@Composable
private fun rememberConflictSummary(
	entity: ProjectSynchronization.EntityConflict<*>?,
	projectData: ProjectSynchronization.ProjectDataConflictState?,
): ConflictSummary {
	val sceneLabel = Res.string.sync_conflict_entity_label_scene.get()
	val noteLabel = Res.string.sync_conflict_entity_label_note.get()
	val timelineLabel = Res.string.sync_conflict_entity_label_timeline_event.get()
	val encyclopediaLabel = Res.string.sync_conflict_entity_label_encyclopedia_entry.get()
	val sceneDraftLabel = Res.string.sync_conflict_entity_label_scene_draft.get()
	val imageLabel = Res.string.sync_conflict_entity_label_image.get()
	val projectDataLabel = Res.string.sync_conflict_entity_label_project_data.get()
	return remember(entity, projectData) {
		when (entity) {
			is ProjectSynchronization.EntityConflict.SceneConflict -> ConflictSummary(
				label = sceneLabel,
				name = entity.serverEntity.name,
				meta = null,
			)
			is ProjectSynchronization.EntityConflict.NoteConflict -> ConflictSummary(
				label = noteLabel,
				name = entity.serverEntity.content.firstNonBlankLine().truncateWithEllipsis(40),
				meta = null,
			)
			is ProjectSynchronization.EntityConflict.TimelineEventConflict -> {
				val event = entity.serverEntity
				ConflictSummary(
					label = timelineLabel,
					name = (event.title?.takeIf { it.isNotBlank() }
						?: event.cachedTitle?.takeIf { it.isNotBlank() }
						?: event.content.firstNonBlankLine()).truncateWithEllipsis(40),
					meta = event.label?.takeIf { it.isNotBlank() } ?: event.start?.toCompactText(),
				)
			}
			is ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict -> ConflictSummary(
				label = encyclopediaLabel,
				name = entity.serverEntity.name,
				meta = null,
			)
			is ProjectSynchronization.EntityConflict.SceneDraftConflict -> ConflictSummary(
				label = sceneDraftLabel,
				name = entity.serverEntity.name,
				meta = null,
			)
			is ProjectSynchronization.EntityConflict.ImageConflict -> ConflictSummary(
				label = imageLabel,
				name = entity.serverEntity.name,
				meta = entity.serverEntity.fileExtension.uppercase(),
			)
			null -> ConflictSummary(
				label = projectDataLabel,
				name = null,
				meta = null,
			)
		}
	}
}

@Composable
private fun ConflictBody(
	summary: ConflictSummary,
	infoMessage: String?,
	onInfoClick: (String) -> Unit,
	content: @Composable () -> Unit,
) {
	Column(modifier = Modifier.fillMaxSize()) {
		ConflictHeaderStrip(
			summary = summary,
			infoMessage = infoMessage,
			onInfoClick = if (infoMessage != null) onInfoClick else null,
		)
		Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
			content()
		}
	}
}

@Composable
private fun ConflictHeaderStrip(
	summary: ConflictSummary,
	infoMessage: String?,
	onInfoClick: ((String) -> Unit)?,
) {
	Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			HdWarnGlyph(size = 22.dp)
			Column(modifier = Modifier.weight(1f)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = Res.string.sync_conflict_header_title.get(summary.label),
						style = MaterialTheme.typography.titleLarge,
						color = MaterialTheme.colorScheme.onSurface,
					)
					if (infoMessage != null && onInfoClick != null) {
						Spacer(modifier = Modifier.size(Ui.Padding.M))
						Icon(
							imageVector = Icons.Default.Info,
							contentDescription = infoMessage,
							modifier = Modifier
								.size(18.dp)
								.clickable { onInfoClick(infoMessage) },
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
				if (summary.name != null || summary.meta != null) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
					) {
						if (summary.name != null) {
							Text(
								text = "“${summary.name}”",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						if (summary.meta != null) {
							HdMonoLabel(
								text = "· ${summary.meta}",
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}
			}
		}
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}
