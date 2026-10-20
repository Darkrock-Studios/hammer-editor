package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable

/** Touch platforms reach the same actions through the row's overflow menu. */
@Composable
actual fun ProjectGroupActionContainer(
	items: List<ProjectGroupMenuItem>,
	content: @Composable () -> Unit,
) {
	content()
}
