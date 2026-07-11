package com.darkrockstudios.apps.hammer.common.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import io.github.vinceglb.filekit.PlatformFile

@Composable
actual fun Modifier.fileDropTarget(
	enabled: Boolean,
	extensions: Set<String>,
	onDragChange: (Boolean) -> Unit,
	onFilesDropped: (List<PlatformFile>) -> Unit,
): Modifier {
	if (!enabled) return this

	val activity = LocalContext.current.findActivity()
	val currentOnDragChange by rememberUpdatedState(onDragChange)
	val currentOnFilesDropped by rememberUpdatedState(onFilesDropped)

	val target = remember(activity) {
		object : DragAndDropTarget {
			override fun onEntered(event: DragAndDropEvent) = currentOnDragChange(true)
			override fun onExited(event: DragAndDropEvent) = currentOnDragChange(false)
			override fun onEnded(event: DragAndDropEvent) = currentOnDragChange(false)

			override fun onDrop(event: DragAndDropEvent): Boolean {
				currentOnDragChange(false)
				val act = activity ?: return false
				val dragEvent = event.toAndroidDragEvent()
				val clipData = dragEvent.clipData ?: return false

				// Grant read access to the source app's content URIs. The grant is held for the
				// activity's lifetime so the async staging read below still has permission.
				act.requestDragAndDropPermissions(dragEvent) ?: return false

				val files = (0 until clipData.itemCount)
					.mapNotNull { clipData.getItemAt(it).uri }
					.map { PlatformFile(it) }
				if (files.isEmpty()) return false

				currentOnFilesDropped(files)
				return true
			}
		}
	}

	return this.dragAndDropTarget(
		shouldStartDragAndDrop = { event ->
			event.toAndroidDragEvent().clipDescription?.hasMimeType("image/*") == true
		},
		target = target,
	)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.findActivity()
	else -> null
}
