package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.Project

@Composable
fun ProjectSelectionUi(component: ProjectSelectionComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    var projectDirText by remember { mutableStateOf(state.projectsDir) }

    Column(modifier = modifier.padding(Ui.PADDING)) {
        Text(
            text = "Projects Directory",
            style = MaterialTheme.typography.h4
        )

        TextField(
            value = projectDirText,
            onValueChange = {
                projectDirText = it
                component.setProjectsDir(it)
            },
            label = { Text("Projects Directory") }
        )

        Button(onClick = { component.loadProjectList() }) {
            Text("Load")
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
            items(state.projects.size) { index ->
                ProjectCard(state.projects[index], component::selectProject)
            }
        }
    }
}

@Composable
fun ProjectCard(project: Project, onProjectClick: (project: Project) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Ui.PADDING)
            .clickable { onProjectClick(project) },
        elevation = Ui.ELEVATION
    ) {
        Column(modifier = Modifier.padding(Ui.PADDING)) {
            Text(
                "Project: $project",
                style = MaterialTheme.typography.body1
            )
        }
    }
}