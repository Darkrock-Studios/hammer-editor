package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.reauthentication.ReauthenticationUi
import com.darkrockstudios.apps.hammer.project_select_list_header
import com.darkrockstudios.apps.hammer.project_select_project_list_empty
import com.darkrockstudios.apps.hammer.projects_list_sync_button

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProjectListUi(
	component: ProjectsList,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier
) {
	val windowSizeClass = calculateWindowSizeClass()
	val state by component.state.subscribeAsState()

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
					Res.string.project_select_list_header.get(),
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
						Image(Icons.Default.Refresh, Res.string.projects_list_sync_button.get())
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
								Res.string.project_select_project_list_empty.get(),
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
							onProjectAltClick = { project -> component.showProjectDelete(project) },
							onProjectRenameClick = { project -> component.showProjectRename(project) },
						)
					}
				}
				MpScrollBarList(state = listState)
			}
		}
	}

	ModalContent(component, rootSnackbar)
}

@Composable
fun ModalContent(component: ProjectsList, rootSnackbar: RootSnackbarHostState) {
	val state by component.modalRouterState.subscribeAsState()
	val overlay = state.child?.instance
	when (overlay) {
		null, ProjectsList.ModalDestination.None -> {}
		is ProjectsList.ModalDestination.ProjectSync -> {
			ProjectsSyncDialog(component, rootSnackbar)
		}

		is ProjectsList.ModalDestination.ProjectRename -> {
			ProjectRenameDialog(
				component = component,
				projectDef = overlay.projectDef,
				close = { component.dismissProjectRename() }
			)
		}

		is ProjectsList.ModalDestination.ProjectCreate -> {
			ProjectCreateDialog(true, component) {
				component.hideCreate()
			}
		}

		is ProjectsList.ModalDestination.ProjectDelete -> {
			ProjectDeleteDialog(
				component = component,
				projectDef = overlay.projectDef,
				close = { component.dismissProjectDelete() }
			)
		}

		is ProjectsList.ModalDestination.ServerReauth -> {
			ReauthenticationUi(overlay.component)
		}
	}
}
