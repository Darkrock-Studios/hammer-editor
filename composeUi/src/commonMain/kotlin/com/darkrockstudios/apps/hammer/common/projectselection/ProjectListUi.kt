package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import dev.icerock.moko.resources.compose.stringResource

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProjectListUi(
	component: ProjectsList,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier
) {
	val windowSizeClass = calculateWindowSizeClass()
	val state by component.state.subscribeAsState()
	var projectDefDeleteTarget by rememberSaveable { mutableStateOf<ProjectDef?>(null) }
	var projectDefRenameTarget by rememberSaveable { mutableStateOf<ProjectDef?>(null) }

	Toaster(component, rootSnackbar)

	val colModifier: Modifier = when (windowSizeClass.widthSizeClass) {
		WindowWidthSizeClass.Compact -> modifier.fillMaxWidth()
		WindowWidthSizeClass.Medium -> modifier.fillMaxWidth()
		WindowWidthSizeClass.Expanded -> modifier.widthIn(max = 600.dp).fillMaxSize()
		else -> error("Unhandled window class size: ${windowSizeClass.widthSizeClass}")
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = colModifier.align(Alignment.TopCenter)
		) {
			Row {
				Text(
					"\uD83D\uDCDD",
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.padding(start = Ui.Padding.L, bottom = Ui.Padding.L)
				)

				Text(
					MR.strings.project_select_list_header.get(),
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.weight(1f)
						.padding(start = Ui.Padding.L, bottom = Ui.Padding.L)
				)

				if (state.isServerSynced) {
					Button(
						onClick = {
							component.showProjectsSync()
						},
						modifier = Modifier.padding(end = Ui.Padding.XL),
					) {
						Image(Icons.Default.Refresh, MR.strings.projects_list_sync_button.get())
					}
				}
			}

			Row(modifier = Modifier.fillMaxWidth()) {
				val listState: LazyListState = rememberLazyListState()
				LazyColumn(
					modifier = Modifier.weight(1f),
					state = listState,
					contentPadding = PaddingValues(horizontal = Ui.Padding.XL),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)
				) {
					if (state.projects.isEmpty()) {
						item {
							Text(
								MR.strings.project_select_project_list_empty.get(),
								modifier = Modifier.padding(Ui.Padding.L).fillMaxWidth(),
								style = MaterialTheme.typography.headlineSmall,
								textAlign = TextAlign.Center,
								fontStyle = FontStyle.Italic,
								color = MaterialTheme.colorScheme.onBackground
							)
						}
					}

					items(
						count = state.projects.size,
						key = { index -> state.projects[index].definition.name.hashCode() }
					) { index ->
						ProjectCard(
							projectData = state.projects[index],
							onProjectClick = component::selectProject,
							onProjectAltClick = { project -> projectDefDeleteTarget = project },
							onProjectRenameClick = { project -> projectDefRenameTarget = project },
						)
					}
				}
				MpScrollBarList(state = listState)
			}
		}
	}

	ProjectCreateDialog(state.showCreateDialog, component) {
		component.hideCreate()
	}

	projectDefDeleteTarget?.let { project ->
		SimpleConfirm(
			title = MR.strings.delete_project_title.get(),
			message = stringResource(MR.strings.delete_project_message, project.name),
			onDismiss = { projectDefDeleteTarget = null },
			onConfirm = {
				component.deleteProject(project)
				projectDefDeleteTarget = null
			}
		)
	}

	ProjectRenameDialog(
		component = component,
		projectDef = projectDefRenameTarget,
		close = { projectDefRenameTarget = null }
	)

	ProjectsSyncDialog(component)
}
