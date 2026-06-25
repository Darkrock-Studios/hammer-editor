package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.retryingFileDialog
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun ImportFilePicker(
	show: Boolean,
	scope: CoroutineScope,
	onFileSelected: (name: String, content: ByteArray) -> Unit,
	onCancel: () -> Unit,
) {
	val defaultDispatcher = rememberDefaultDispatcher()

	LaunchedEffect(show) {
		if (show) {
			val file = retryingFileDialog {
				FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("md", "markdown", "rtf")))
			}
			if (file != null) {
				scope.launch {
					val content = withContext(defaultDispatcher) {
						try {
							file.readBytes()
						} catch (@Suppress("TooGenericExceptionCaught") e: Exception) { // file read can fail many ways
							Napier.e("Failed to read import file", e)
							null
						}
					}
					if (content != null) {
						onFileSelected(file.name, content)
					} else {
						onCancel()
					}
				}
			} else {
				onCancel()
			}
		}
	}
}
