package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable

/** Adds the platform's secondary-click menu to a group row: a context menu on desktop. */
@Composable
expect fun ProjectGroupActionContainer(
	items: List<ProjectGroupMenuItem>,
	content: @Composable () -> Unit,
)
