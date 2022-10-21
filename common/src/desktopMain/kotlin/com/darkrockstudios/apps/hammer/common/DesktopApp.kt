// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.darkrockstudios.apps.hammer.common

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionComponent

@Preview
@Composable
fun AppPreview(
    componentContext: ComponentContext,
    onProjectSelected: (projectDef: ProjectDef) -> Unit
) {
    ProjectSelectionComponent(
        componentContext = componentContext,
        showProjectDirectory = true,
        onProjectSelected = onProjectSelected
    )
}