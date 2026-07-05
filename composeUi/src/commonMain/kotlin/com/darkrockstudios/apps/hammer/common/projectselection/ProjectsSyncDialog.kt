package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.account_sync_confirm_cancel_message
import com.darkrockstudios.apps.hammer.account_sync_confirm_cancel_title
import com.darkrockstudios.apps.hammer.account_sync_dialog_header
import com.darkrockstudios.apps.hammer.account_sync_dialog_status_canceled
import com.darkrockstudios.apps.hammer.account_sync_dialog_status_complete
import com.darkrockstudios.apps.hammer.account_sync_dialog_status_conflict
import com.darkrockstudios.apps.hammer.account_sync_dialog_status_error
import com.darkrockstudios.apps.hammer.account_sync_dialog_status_pending
import com.darkrockstudios.apps.hammer.account_sync_log_filter_all
import com.darkrockstudios.apps.hammer.account_sync_log_filter_label
import com.darkrockstudios.apps.hammer.account_sync_log_title
import com.darkrockstudios.apps.hammer.account_sync_toast_canceled
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.hasActiveSync
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFilterMenu
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineProgressBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdLogGlyph
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdStatus
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdStatusGlyph
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdToolButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.accentColor
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.projectsync.IdeaConflictUi
import com.darkrockstudios.apps.hammer.ideas_conflict_title
import kotlinx.coroutines.launch

private val DialogMaxWidth = 580.dp
private val DialogBodyMinHeight = 280.dp
private val DialogBodyMaxHeight = 560.dp

@Composable
fun ProjectsSyncDialog(component: ProjectsList, rootSnackbar: RootSnackbarHostState) {
	val state by component.state.subscribeAsState()
	var isOpen by remember { mutableStateOf(true) }
	var confirmCancel by rememberSaveable { mutableStateOf(false) }
	var showLog by rememberSaveable { mutableStateOf(false) }
	val allProjectsLabel = Res.string.account_sync_log_filter_all.get()
	// null == all projects; storing the project name (not the localized label) keeps the
	// selection stable across locale changes.
	var logProjectFilter by rememberSaveable { mutableStateOf<String?>(null) }
	val syncCanceledText = Res.string.account_sync_toast_canceled.get()
	val scope = rememberCoroutineScope()

	val requestClose = {
		if (state.syncState.hasActiveSync) confirmCancel = true else isOpen = false
	}

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = requestClose,
		onClosed = { component.hideProjectsSync() },
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Surface(
			modifier = Modifier
				.padding(Ui.Padding.M)
				.widthIn(max = DialogMaxWidth)
				.fillMaxWidth()
				.predictiveBackTransform(),
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
					inLogView = showLog,
					state = state.syncState,
					onClose = requestClose,
				)
				HdFolioDivider()

				TitleAndToolbar(
					inLogView = showLog,
					inConflictView = state.syncState.ideaConflict != null,
					syncComplete = state.syncState.syncComplete,
					onStop = { component.cancelProjectsSync() },
					onToggleLog = { showLog = !showLog },
				)

				if (showLog) {
					val projectNames = remember(state.syncState.projectsStatus) {
						state.syncState.projectsStatus.keys.sorted()
					}
					LogFilterStrip(
						allProjectsLabel = allProjectsLabel,
						projectNames = projectNames,
						selected = logProjectFilter,
						onSelect = { logProjectFilter = it },
					)
				} else {
					OverallProgressStrip(state.syncState)
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = DialogBodyMinHeight, max = DialogBodyMaxHeight),
				) {
					val ideaConflict = state.syncState.ideaConflict
					if (ideaConflict != null) {
						// The dialog is too narrow for side-by-side panes; use the tabbed layout.
						IdeaConflictUi(
							conflict = ideaConflict,
							compact = true,
							onResolve = { component.resolveIdeaConflict(it) },
						)
					} else if (showLog) {
						val showAll = logProjectFilter == null
						val filteredLog = remember(state.syncState.syncLog, logProjectFilter) {
							if (logProjectFilter == null) {
								state.syncState.syncLog
							} else {
								state.syncState.syncLog.filter { it.projectName == logProjectFilter }
							}
						}
						LogList(log = filteredLog, showProjectName = showAll)
					} else {
						val projects = remember(state.syncState.projectsStatus) {
							state.syncState.projectsStatus.values.toList()
						}
						ProgressList(projects)
					}
				}

				FooterBar(inLogView = showLog)
			}
		}
	}

	if (confirmCancel) {
		SimpleConfirm(
			title = Res.string.account_sync_confirm_cancel_title.get(),
			message = Res.string.account_sync_confirm_cancel_message.get(),
			onDismiss = { confirmCancel = false },
			onConfirm = {
				component.cancelProjectsSync()
				confirmCancel = false
				isOpen = false
				scope.launch { rootSnackbar.showSnackbar(syncCanceledText) }
			},
		)
	}
}

@Composable
private fun Masthead(
	inLogView: Boolean,
	state: ProjectsList.SyncState,
	onClose: () -> Unit,
) {
	val total = state.projectsStatus.size
	val done = remember(state.projectsStatus) {
		state.projectsStatus.values.count { it.status == ProjectsList.Status.Complete }
	}
	val meta = when {
		inLogView -> "${state.syncLog.size} ENTRIES"
		state.syncComplete -> "$done / $total · DONE"
		else -> "$done / $total · IN PROGRESS"
	}

	HdMasthead(
		section = "ACCOUNT",
		leadingMeta = listOf(meta),
		trailing = { HdMastheadAction(label = "× CLOSE", onClick = onClose) },
	)
}

@Composable
private fun TitleAndToolbar(
	inLogView: Boolean,
	inConflictView: Boolean,
	syncComplete: Boolean,
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
			text = when {
				inConflictView -> Res.string.ideas_conflict_title.get()
				inLogView -> Res.string.account_sync_log_title.get()
				else -> Res.string.account_sync_dialog_header.get()
			},
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Spacer(modifier = Modifier.weight(1f))

		if (!syncComplete) {
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
private fun LogFilterStrip(
	allProjectsLabel: String,
	projectNames: List<String>,
	selected: String?,
	onSelect: (String?) -> Unit,
) {
	val options = remember(allProjectsLabel, projectNames) {
		listOf(allProjectsLabel) + projectNames
	}
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
	) {
		HdFilterMenu(
			label = Res.string.account_sync_log_filter_label.get(),
			options = options,
			selected = selected ?: allProjectsLabel,
			onSelect = { picked -> onSelect(picked.takeIf { it != allProjectsLabel }) },
		)
	}
}

@Composable
private fun OverallProgressStrip(state: ProjectsList.SyncState) {
	val (done, pct) = remember(state.projectsStatus) {
		val values = state.projectsStatus.values
		val total = values.size.coerceAtLeast(1)
		var sum = 0.0
		var d = 0
		for (s in values) {
			sum += s.progress
			if (s.status == ProjectsList.Status.Complete) d++
		}
		d to (sum / total).coerceIn(0.0, 1.0).toFloat()
	}
	val totalDisplay = state.projectsStatus.size

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdMonoLabel(
			text = "${done.toString().padStart(2, '0')} / ${totalDisplay.toString().padStart(2, '0')}",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdHairlineProgressBar(
			progress = pct,
			modifier = Modifier.weight(1f),
		)
		HdMonoLabel(
			text = "${(pct * 100).toInt()}%",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun ProgressList(projects: List<ProjectsList.ProjectSyncStatus>) {
	val listState: LazyListState = rememberLazyListState()
	LazyColumn(state = listState) {
		items(count = projects.size, key = { projects[it].projectName }) { index ->
			SyncStatusRow(
				projectStatus = projects[index],
				isLast = index == projects.size - 1,
			)
		}
	}
}

@Composable
internal fun SyncStatusRow(
	projectStatus: ProjectsList.ProjectSyncStatus,
	isLast: Boolean,
) {
	val hdStatus = when (projectStatus.status) {
		ProjectsList.Status.Pending -> HdStatus.Pending
		ProjectsList.Status.Syncing -> HdStatus.Syncing
		ProjectsList.Status.Failed -> HdStatus.Failed
		ProjectsList.Status.NeedsResolution -> HdStatus.Warning
		ProjectsList.Status.Complete -> HdStatus.Complete
		ProjectsList.Status.Canceled -> HdStatus.Canceled
	}
	val metaText = when (projectStatus.status) {
		ProjectsList.Status.Pending -> Res.string.account_sync_dialog_status_pending.get()
		ProjectsList.Status.Syncing -> "${(projectStatus.progress * 100).toInt()}%"
		ProjectsList.Status.Failed -> Res.string.account_sync_dialog_status_error.get()
		ProjectsList.Status.NeedsResolution -> Res.string.account_sync_dialog_status_conflict.get()
		ProjectsList.Status.Complete -> Res.string.account_sync_dialog_status_complete.get()
		ProjectsList.Status.Canceled -> Res.string.account_sync_dialog_status_canceled.get()
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.L,
				bottom = Ui.Padding.M,
			),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			HdStatusGlyph(hdStatus)
			Text(
				text = projectStatus.projectName,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
			)
			HdMonoLabel(
				text = metaText,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Spacer(modifier = Modifier.height(Ui.Padding.M))
		HdHairlineProgressBar(
			progress = projectStatus.progress,
			color = hdStatus.accentColor(),
		)
	}
	if (!isLast) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}

@Composable
private fun LogList(log: List<SyncLogMessage>, showProjectName: Boolean) {
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
			SyncLogMessageUi(log[index], showProjectName = showProjectName)
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
			text = if (inLogView) "LIVE · TAIL" else "AUTO · SYNC",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = "ESC CLOSE",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
