package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.darkrockstudios.apps.hammer.common.compose.rememberWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.projectselection.SyncLogMessageUi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DialogMaxWidth = 800.dp
private val DialogBodyMinHeight = 280.dp

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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

	Surface(
		modifier = modifier
			.padding(Ui.Padding.M)
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		),
	) {
		Column {
			Masthead(
				inLogView = state.showLog,
				state = state,
				onClose = onClose,
			)
			HdFolioDivider()

			TitleAndToolbar(
				inLogView = state.showLog,
				isSyncing = state.isSyncing,
				onStop = { component.cancelSync() },
				onToggleLog = { component.showLog(!state.showLog) },
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
						}
					}

					state.showLog -> LogList(state.syncLog)

					else -> StatusBody(state)
				}

				SnackbarHost(
					hostState = snackbarHostState,
					modifier = Modifier.align(Alignment.BottomCenter),
				)
			}

			FooterBar(inLogView = state.showLog)
		}
	}
}

@Composable
private fun Masthead(
	inLogView: Boolean,
	state: ProjectSynchronization.State,
	onClose: () -> Unit,
) {
	val meta = when {
		inLogView -> "${state.syncLog.size} ENTRIES"
		state.failed -> "FAILED"
		state.isSyncing -> "${(state.syncProgress * 100).toInt()}% · IN PROGRESS"
		else -> "DONE"
	}

	HdMasthead(
		section = "PROJECT",
		leadingMeta = listOf(meta),
		trailing = { HdMastheadAction(label = "× CLOSE", onClick = onClose) },
	)
}

@Composable
private fun TitleAndToolbar(
	inLogView: Boolean,
	isSyncing: Boolean,
	onStop: () -> Unit,
	onToggleLog: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.L,
				bottom = Ui.Padding.M,
			),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Text(
			text = if (inLogView) {
				Res.string.account_sync_log_title.get()
			} else {
				Res.string.sync_project_dialog_title.get()
			},
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Spacer(modifier = Modifier.weight(1f))

		if (isSyncing) {
			HdToolButton(active = false, onClick = onStop) {
				Box(
					modifier = Modifier
						.size(9.dp)
						.background(MaterialTheme.colorScheme.onSurfaceVariant),
				)
			}
		}

		HdToolButton(active = inLogView, onClick = onToggleLog) {
			HdLogGlyph()
		}
	}
}

@Composable
private fun OverallProgressStrip(state: ProjectSynchronization.State) {
	val (status, statusWord) = when {
		state.failed -> HdStatus.Failed to "FAILED"
		state.isSyncing -> HdStatus.Syncing to "SYNCING"
		else -> HdStatus.Complete to "DONE"
	}
	val pct = (state.syncProgress * 100).toInt()

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdMonoLabel(
			text = statusWord,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdHairlineProgressBar(
			progress = state.syncProgress,
			color = status.accentColor(),
			modifier = Modifier.weight(1f),
		)
		HdMonoLabel(
			text = "$pct%",
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

@Composable
private fun LogList(log: List<SyncLogMessage>) {
	val listState: LazyListState = rememberLazyListState()

	LazyColumn(
		state = listState,
		modifier = Modifier.fillMaxWidth(),
		contentPadding = PaddingValues(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
	) {
		items(
			count = log.size,
			key = { log[it].timestamp.toEpochMilliseconds() to it },
		) { index ->
			SyncLogMessageUi(log[index], showProjectName = false)
		}
	}

	val pinnedToTail by remember {
		derivedStateOf {
			val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
			last < 0 || last >= log.size - 2
		}
	}
	LaunchedEffect(log.size) {
		if (log.isNotEmpty() && pinnedToTail) {
			listState.scrollToItem(log.size - 1)
		}
	}
}

@Composable
private fun FooterBar(inLogView: Boolean) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
	) {
		HdMonoLabel(
			text = if (inLogView) "LIVE · TAIL" else "PROJECT · SYNC",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = "ESC CLOSE",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
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
			is ProjectSynchronization.EntityConflict.TimelineEventConflict -> ConflictSummary(
				label = timelineLabel,
				name = entity.serverEntity.content.firstNonBlankLine().truncateWithEllipsis(40),
				meta = entity.serverEntity.date?.takeIf { it.isNotBlank() },
			)
			is ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict -> ConflictSummary(
				label = encyclopediaLabel,
				name = entity.serverEntity.name,
				meta = "ENC · ${entity.serverEntity.entryType.uppercase()}",
			)
			is ProjectSynchronization.EntityConflict.SceneDraftConflict -> ConflictSummary(
				label = sceneDraftLabel,
				name = entity.serverEntity.name,
				meta = null,
			)
			null -> ConflictSummary(
				label = if (projectData != null) projectDataLabel else "Conflict",
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
