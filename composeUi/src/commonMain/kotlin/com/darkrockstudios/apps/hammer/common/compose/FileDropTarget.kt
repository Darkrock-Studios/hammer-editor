package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

/**
 * Accepts files dragged onto this element from outside the app. Implemented on desktop and
 * Android. A no-op on iOS: Compose exposes no public API for the drop-session payload
 * (`DragAndDropEvent.dropSessionContext` is internal), so reading dropped files needs a native
 * `UIDropInteraction` path not yet built here.
 *
 * @param enabled when false the modifier is inert.
 * @param extensions lowercase extensions (without the dot) to accept; empty accepts any. Applied
 *   on desktop by file name; Android gates on an `image*` MIME type instead.
 * @param onDragChange invoked true while a droppable drag hovers the target, false otherwise.
 */
@Composable
expect fun Modifier.fileDropTarget(
	enabled: Boolean,
	extensions: Set<String>,
	onDragChange: (Boolean) -> Unit,
	onFilesDropped: (List<PlatformFile>) -> Unit,
): Modifier
