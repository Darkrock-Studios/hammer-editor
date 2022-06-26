package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun ProjectSelectionUi(component: ProjectSelectionComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    var newProjectNameText by remember { mutableStateOf("") }
    var projectDefDeleteTarget by remember { mutableStateOf<ProjectDef?>(null) }

    Column(modifier = modifier.padding(Ui.PADDING)) {
        TextField(
            value = newProjectNameText,
            onValueChange = { newProjectNameText = it },
            label = { Text("New Project Name") }
        )

        Button(onClick = {
            component.createProject(newProjectNameText)
            newProjectNameText = ""
        }) {
            Text("Create Project")
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(Ui.PADDING)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\uD83D\uDCDD Projects:",
                        style = MaterialTheme.typography.h5
                    )
                }
            }
            items(state.projectDefs.size) { index ->
                ProjectCard(state.projectDefs[index], component::selectProject) { project ->
                    projectDefDeleteTarget = project
                }
            }
        }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Ui.PADDING)
            .combinedClickable(
                onClick = { onProjectClick(projectDef) },
                onLongClick = { onProjectAltClick(projectDef) }
            ),
        elevation = Ui.ELEVATION
    ) {
        Column(modifier = Modifier.padding(Ui.PADDING)) {
            Text(
                projectDef.name,
                modifier = Modifier.padding(bottom = Ui.PADDING),
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Path: ${projectDef.path}",
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun projectDeleteDialog(projectDef: ProjectDef, dismissDialog: (Boolean) -> Unit) {
    AlertDialog(
        title = { Text("Delete Project") },
        text = { Text("Are you sure you want to delete this project: ${projectDef.name}") },
        onDismissRequest = { dismissDialog(false) },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { dismissDialog(true) }) {
                    Text("DELETE")
                }
                Button(onClick = { dismissDialog(false) }) {
                    Text("Dismiss")
                }
            }
        },
        modifier = Modifier.width(300.dp).padding(Ui.PADDING)
    )
}