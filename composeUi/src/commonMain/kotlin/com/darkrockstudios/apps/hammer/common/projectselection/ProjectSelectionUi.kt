package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBar
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker

fun getLocationIcon(location: ProjectSelection.Locations): ImageVector {
	return when (location) {
		ProjectSelection.Locations.Projects -> Icons.Filled.LibraryBooks
		ProjectSelection.Locations.Sittings -> Icons.Filled.Settings
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun ProjectSelectionUi(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()
	when (state.location) {
		ProjectSelection.Locations.Projects -> ProjectList(component, modifier)
		ProjectSelection.Locations.Sittings -> Settings(component, modifier)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()

	var projectsPathText by remember { mutableStateOf(state.projectsDir.path) }
	var showDirectoryPicker by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.padding(Ui.Padding.L)
			.fillMaxSize()
	) {
		Text(
			"\u2699 Settings",
			style = MaterialTheme.typography.headlineLarge,
			color = MaterialTheme.colorScheme.onBackground,
			modifier = Modifier.padding(Ui.Padding.L)
		)

		Divider(modifier = Modifier.fillMaxWidth())

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		Column(modifier = Modifier.padding(Ui.Padding.M)) {
			Text(
				"Projects Directory",
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onBackground,
			)

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			TextField(
				value = projectsPathText,
				onValueChange = { projectsPathText = it },
				enabled = false,
				label = { Text("Path to Projects Directory") }
			)

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			Button(onClick = { showDirectoryPicker = true }) {
				Text("Select Dir")
			}
		}
	}

	DirectoryPicker(showDirectoryPicker) { path ->
		showDirectoryPicker = false

		if (path != null) {
			projectsPathText = path
			component.setProjectsDir(projectsPathText)
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
fun ProjectList(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()
	var projectDefDeleteTarget by remember { mutableStateOf<ProjectDef?>(null) }
	var showProjectCreate by remember { mutableStateOf(false) }

	Box {
		Column(
			modifier = modifier
				.padding(Ui.Padding.XL)
				.fillMaxSize()
		) {
			Text(
				"\uD83D\uDCDD Projects",
				style = MaterialTheme.typography.headlineLarge,
				color = MaterialTheme.colorScheme.onBackground,
				modifier = Modifier.padding(Ui.Padding.L)
			)
			Row(modifier = Modifier.fillMaxWidth()) {
				val listState: LazyListState = rememberLazyListState()
				LazyColumn(
					modifier = Modifier.weight(1f),
					state = listState,
					contentPadding = PaddingValues(Ui.Padding.XL),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)
				) {
					items(
						count = state.projectDefs.size,
						key = { index -> state.projectDefs[index].name.hashCode() }
					) { index ->
						ProjectCard(state.projectDefs[index], component::selectProject) { project ->
							projectDefDeleteTarget = project
						}
					}
				}
				MpScrollBar(state = listState)
			}
		}
		FloatingActionButton(
			onClick = { showProjectCreate = true },
			modifier = Modifier.align(Alignment.BottomEnd).padding(Ui.Padding.M),
		) {
			Icon(imageVector = Icons.Filled.Create, "Create Project")
		}
	}

	ProjectCreateDialog(showProjectCreate, component) {
		showProjectCreate = false
	}

	projectDefDeleteTarget?.let { project ->
		projectDeleteDialog(project) { deleteProject ->
			if (deleteProject) {
				component.deleteProject(project)
			}

			projectDefDeleteTarget = null
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
	projectDef: ProjectDef,
	onProjectClick: (projectDef: ProjectDef) -> Unit,
	onProjectAltClick: (projectDef: ProjectDef) -> Unit
) {
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = { onProjectClick(projectDef) },
			),
		tonalElevation = Ui.Elevation.SMALL
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL)) {
			Row(modifier = Modifier.padding(bottom = Ui.Padding.S).fillMaxWidth()) {
				Text(
					projectDef.name,
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.headlineMedium,
					fontWeight = FontWeight.Bold
				)
				IconButton(onClick = { onProjectAltClick(projectDef) }, modifier = Modifier) {
					Icon(
						imageVector = Icons.Filled.Delete,
						contentDescription = "Delete",
						modifier = Modifier.size(24.dp),
					)
				}
			}

			Text(
				"Created: 1/12/93",
				modifier = Modifier.padding(bottom = Ui.Padding.M, start = Ui.Padding.L),
				style = MaterialTheme.typography.bodySmall
			)
			Divider(modifier = Modifier.fillMaxWidth().height(1.dp))
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreateDialog(show: Boolean, component: ProjectSelection, close: () -> Unit) {
	MpDialog(
		onCloseRequest = close,
		visible = show,
		title = "Create Project",
		modifier = Modifier.padding(Ui.Padding.XL)
	) {
		var newProjectNameText by remember { mutableStateOf("") }
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				TextField(
					value = newProjectNameText,
					onValueChange = { newProjectNameText = it },
					label = { Text("New Project Name") },
				)

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				Button(onClick = {
					component.createProject(newProjectNameText)
					newProjectNameText = ""
					close()
				}) {
					Text("Create Project")
				}
			}
		}
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun projectDeleteDialog(projectDef: ProjectDef, dismissDialog: (Boolean) -> Unit) {
	MpDialog(
		onCloseRequest = {},
		visible = true,
		modifier = Modifier.padding(Ui.Padding.XL),
		title = "Delete Project"
	) {
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				Text(
					"Are you sure you want to delete this project:\n${projectDef.name}",
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.L),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = { dismissDialog(true) }) {
						Text("DELETE")
					}
					Button(onClick = { dismissDialog(false) }) {
						Text("Dismiss")
					}
				}
			}
		}
	}
}