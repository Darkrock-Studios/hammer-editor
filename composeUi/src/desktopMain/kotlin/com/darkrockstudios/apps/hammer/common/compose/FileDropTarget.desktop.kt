package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import io.github.vinceglb.filekit.PlatformFile
import java.awt.datatransfer.DataFlavor
import java.io.File

@Composable
actual fun Modifier.fileDropTarget(
	enabled: Boolean,
	extensions: Set<String>,
	onDragChange: (Boolean) -> Unit,
	onFilesDropped: (List<PlatformFile>) -> Unit,
): Modifier {
	if (!enabled) return this

	val currentExtensions by rememberUpdatedState(extensions)
	val currentOnDragChange by rememberUpdatedState(onDragChange)
	val currentOnFilesDropped by rememberUpdatedState(onFilesDropped)

	val target = remember {
		object : DragAndDropTarget {
			override fun onEntered(event: DragAndDropEvent) = currentOnDragChange(true)
			override fun onExited(event: DragAndDropEvent) = currentOnDragChange(false)
			override fun onEnded(event: DragAndDropEvent) = currentOnDragChange(false)

			override fun onDrop(event: DragAndDropEvent): Boolean {
				currentOnDragChange(false)
				val files = event.droppedFiles(currentExtensions)
				if (files.isEmpty()) return false
				currentOnFilesDropped(files)
				return true
			}
		}
	}

	return this.dragAndDropTarget(
		shouldStartDragAndDrop = { event -> event.containsFiles() },
		target = target,
	)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.containsFiles(): Boolean =
	awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.droppedFiles(extensions: Set<String>): List<PlatformFile> {
	val transferable = awtTransferable
	if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()

	@Suppress("UNCHECKED_CAST")
	val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
	return files
		.filter { file -> extensions.isEmpty() || file.extension.lowercase() in extensions }
		.map { PlatformFile(it) }
}
