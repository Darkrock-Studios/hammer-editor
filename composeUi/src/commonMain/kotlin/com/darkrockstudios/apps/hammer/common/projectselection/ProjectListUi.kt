package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.util.format
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProjectListUi(
	component: ProjectsList,
	modifier: Modifier = Modifier
) {
	val windowSizeClass = calculateWindowSizeClass()
	val state by component.state.subscribeAsState()
	var projectDefDeleteTarget by remember { mutableStateOf<ProjectDef?>(null) }
	val snackbarHostState = remember { SnackbarHostState() }

	Toaster(state.toast, snackbarHostState)

	val colModifier: Modifier = when (windowSizeClass.widthSizeClass) {
		WindowWidthSizeClass.Compact -> modifier.fillMaxWidth()
		WindowWidthSizeClass.Medium -> modifier.fillMaxWidth()
		WindowWidthSizeClass.Expanded -> modifier.widthIn(max = 600.dp)
		else -> error("Unhandled window class size: ${windowSizeClass.widthSizeClass}")
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = colModifier
				.padding(Ui.Padding.XL)
				.align(Alignment.TopCenter)
				.fillMaxSize()
		) {
			Row {
				Text(
					"\uD83D\uDCDD",
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.padding(start = Ui.Padding.L, top = Ui.Padding.L, bottom = Ui.Padding.L)
				)

				Text(
					MR.strings.project_select_list_header.get(),
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.weight(1f).padding(Ui.Padding.L)
				)

				if (state.isServerSynced) {
					Button(onClick = {
						component.showProjectsSync()
					}) {
						Image(Icons.Default.Refresh, MR.strings.projects_list_sync_button.get())
					}
				}
			}

			Row(modifier = Modifier.fillMaxWidth()) {
				val listState: LazyListState = rememberLazyListState()
				LazyColumn(
					modifier = Modifier.weight(1f),
					state = listState,
					contentPadding = PaddingValues(Ui.Padding.XL),
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
						) { project ->
							projectDefDeleteTarget = project
						}
					}
				}
				MpScrollBarList(state = listState)
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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

	ProjectsSyncDialog(component)
}

val ProjectCardTestTag = "project-card"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
	projectData: ProjectData,
	onProjectClick: (projectDef: ProjectDef) -> Unit,
	onProjectAltClick: (projectDef: ProjectDef) -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = { onProjectClick(projectData.definition) },
			)
			.semantics { contentDescription = "Project Card for ${projectData.definition.name}" }
			.testTag(ProjectCardTestTag),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL)) {
			Row(modifier = Modifier.padding(bottom = Ui.Padding.S).fillMaxWidth()) {
				Text(
					projectData.definition.name,
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.headlineMedium,
					fontWeight = FontWeight.Bold,
				)
				IconButton(onClick = { onProjectAltClick(projectData.definition) }) {
					Icon(
						imageVector = Icons.Filled.Delete,
						contentDescription = MR.strings.project_select_card_delete_button.get(),
						modifier = Modifier.size(24.dp),
					)
				}
			}

			val date = remember(projectData.metadata.info) {
				projectData.metadata.info.created.let { instant: Instant ->
					val created = instant.toLocalDateTime(TimeZone.currentSystemDefault())
					created.format("dd MMM `yy")
				}
			}

			Text(
				date,
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