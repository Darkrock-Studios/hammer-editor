package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun ImportFilePicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
) {
	val defaultDispatcher = rememberDefaultDispatcher()

	val filePickerLauncher = rememberFilePickerLauncher(
		type = FileKitType.File(extensions = listOf("md", "markdown")),
	) { file: PlatformFile? ->
		if (file != null) {
			scope.launch {
				val content = withContext(defaultDispatcher) {
					try {
						file.readString()
					} catch (e: Exception) {
						Napier.e("Failed to read import file", e)
						null
					}
				}
				if (content != null) {
					component.selectImportFile(file.name, content)
				} else {
					component.cancelImportFilePicker()
				}
			}
		} else {
			component.cancelImportFilePicker()
		}
	}

	LaunchedEffect(show) {
		if (show) {
			filePickerLauncher.launch()
		}
	}
}
