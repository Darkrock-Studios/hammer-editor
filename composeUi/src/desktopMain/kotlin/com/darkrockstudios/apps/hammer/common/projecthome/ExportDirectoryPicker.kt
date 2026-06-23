package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projecthome.fileExtension
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.retryingFileDialog
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_success
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
) {
	val defaultDispatcher = rememberDefaultDispatcher()
	val state by component.state.subscribeAsState()
	val format = state.exportOptions.format

	LaunchedEffect(show) {
		if (show) {
			val suggested = component.getExportStoryFileName(format)
			val extension = format.fileExtension
			val baseName = suggested.removeSuffix(".$extension")
			val file = retryingFileDialog {
				FileKit.openFileSaver(suggestedName = baseName, extension = extension)
			}
			if (file != null) {
				val options = state.exportOptions
				scope.launch(defaultDispatcher) {
					component.exportProjectToFile(file.absolutePath(), options)
					component.showToast(Res.string.project_home_action_export_toast_success)
				}
			} else {
				component.endProjectExport()
			}
		}
	}
}
