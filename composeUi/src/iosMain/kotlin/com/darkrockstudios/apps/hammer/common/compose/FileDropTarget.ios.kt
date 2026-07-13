package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

@Composable
actual fun Modifier.fileDropTarget(
	enabled: Boolean,
	extensions: Set<String>,
	onDragChange: (Boolean) -> Unit,
	onFilesDropped: (List<PlatformFile>) -> Unit,
): Modifier = this
