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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
internal fun ProjectList(
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
					if (state.projectDefs.isEmpty()) {
						item {
							Text(
								"No Projects Found",
								modifier = Modifier.padding(Ui.Padding.L).fillMaxWidth(),
								style = MaterialTheme.typography.headlineSmall,
								textAlign = TextAlign.Center,
								fontStyle = FontStyle.Italic,
								color = MaterialTheme.colorScheme.onBackground
							)
						}
					}

					items(
						count = state.projectDefs.size,
						key = { index -> state.projectDefs[index].name.hashCode() }
					) { index ->
						ProjectCard(state.projectDefs[index], component::selectProject) { project ->
							projectDefDeleteTarget = project
						}
					}
				}
				MpScrollBarList(state = listState)
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
		ProjectDeleteDialog(project) { deleteProject ->
			if (deleteProject) {
				component.deleteProject(project)
			}

			projectDefDeleteTarget = null
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectCard(
	projectDef: ProjectDef,
	onProjectClick: (projectDef: ProjectDef) -> Unit,
	onProjectAltClick: (projectDef: ProjectDef) -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = { onProjectClick(projectDef) },
			),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL)) {
			Row(modifier = Modifier.padding(bottom = Ui.Padding.S).fillMaxWidth()) {
				Text(
					projectDef.name,
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.headlineMedium,
					fontWeight = FontWeight.Bold,
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
			Divider(
				modifier = Modifier.fillMaxWidth().height(1.dp),
				color = MaterialTheme.colorScheme.outline
			)
		}
	}
}