package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable

@Composable
expect fun LibrariesUi(
	showLibraries: Boolean,
	close: () -> Unit
)