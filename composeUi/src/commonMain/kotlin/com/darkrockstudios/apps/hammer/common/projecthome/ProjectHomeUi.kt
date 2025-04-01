package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.slot.ChildSlot
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHomeContentRouter
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ProjectHomeUi(
	component: ProjectHome,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val scope = rememberCoroutineScope()
	val contentState by component.contentRouterState.subscribeAsState()

	Box(modifier = modifier) {
		if (screen.isWide) {
			Row(
				modifier = Modifier.fillMaxSize()
					.padding(horizontal = Ui.Padding.XL)
			) {
				ContentDestination(
					modifier = Modifier.weight(3f)
						.rightBorder(1.dp, MaterialTheme.colorScheme.outline),
					contentState = contentState,
					state = state
				)
				Actions(
					modifier = Modifier.weight(1f),
					component = component,
					scope = scope,
					rootSnackbar = rootSnackbar
				)
			}
		} else {
			ContentDestination(
				modifier = Modifier.fillMaxSize(),
				contentState = contentState,
				state = state,
				otherContent = {
					Actions(
						modifier = Modifier.fillMaxWidth(),
						component = component,
						scope = scope,
						rootSnackbar = rootSnackbar
					)
				}
			)
		}
	}
}

@Composable
private fun ContentDestination(
	modifier: Modifier,
	contentState: ChildSlot<ProjectHomeContentRouter.Config, ProjectHome.ContentDestination>,
	state: ProjectHome.State,
	otherContent: (@Composable () -> Unit)? = null
) {
	val content = contentState.child?.instance
	when (content) {
		null, is ProjectHome.ContentDestination.Stats -> {
			ProjectStatsUi(
				modifier = modifier,
				state = state,
				otherContent = otherContent
			)
		}

		is ProjectHome.ContentDestination.ProjectSettings -> {
			ProjectSettingsUi(
				modifier = modifier,
				component = content.component,
				otherContent = otherContent
			)
		}
	}
}

@Composable
private fun Actions(
	modifier: Modifier,
	component: ProjectHome,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()

	var toastMessage: String? by remember { mutableStateOf(null) }

	LaunchedEffect(toastMessage) {
		toastMessage?.let { message ->
			if (message.isNotBlank()) {
				scope.launch {
					rootSnackbar.showSnackbar(message)
				}
			}
		}
	}

	Column(modifier = modifier.padding(Ui.Padding.XL)) {
		Text(
			MR.strings.project_home_destinations_header.get(),
			style = MaterialTheme.typography.headlineLarge,
			color = MaterialTheme.colorScheme.onSurface
		)
		HorizontalDivider(modifier = Modifier.fillMaxWidth())
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Button(onClick = component::showProjectSettings) {
			Text(MR.strings.project_home_action_settings_button.get())
		}

		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Button(onClick = component::showProjectStats) {
			Text(MR.strings.project_home_action_stats_button.get())
		}

		Spacer(modifier = Modifier.size(Ui.Padding.XL))

		Text(
			MR.strings.project_home_actions_header.get(),
			style = MaterialTheme.typography.headlineLarge,
			color = MaterialTheme.colorScheme.onSurface
		)
		HorizontalDivider(modifier = Modifier.fillMaxWidth())
		Spacer(modifier = Modifier.size(Ui.Padding.L))

		Button(onClick = component::beginProjectExport) {
			Text(MR.strings.project_home_action_export.get())
		}
		if (state.hasServer) {
			Spacer(modifier = Modifier.size(Ui.Padding.L))
			Button(onClick = component::startProjectSync) {
				Text(MR.strings.project_home_action_sync.get())
			}
		}
		if (component.supportsBackup()) {
			Spacer(modifier = Modifier.size(Ui.Padding.L))
			Button(onClick = {
				component.createBackup { backup ->
					toastMessage = if (backup != null) {
						strRes.get(
							MR.strings.project_home_action_backup_toast_success,
							backup.path.name
						)
					} else {
						strRes.get(MR.strings.project_home_action_backup_toast_failure)
					}
				}
			}) {
				Text(MR.strings.project_home_action_backup.get())
			}
		}
	}

	ExportDirectoryPicker(state.showExportDialog, component, scope, rootSnackbar)
}

@Composable
expect fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
)
