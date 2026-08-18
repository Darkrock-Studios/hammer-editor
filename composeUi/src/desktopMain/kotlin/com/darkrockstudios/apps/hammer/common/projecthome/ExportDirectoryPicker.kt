package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projecthome.fileExtension
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_success
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
) {
	val defaultDispatcher = rememberDefaultDispatcher()

	LaunchedEffect(show) {
		if (show) {
			// Snapshot before the save dialog: it does not block this window, so the
			// options must not be re-read after the suspend.
			val options = component.state.value.exportOptions
			val format = options.format
			val suggested = component.getExportStoryFileName(format)
			val extension = format.fileExtension
			val baseName = suggested.removeSuffix(".$extension")
			val file = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
			if (file != null) {
				scope.launch(defaultDispatcher) {
					// A renderer failure must surface as a toast; an uncaught throw here takes down the app.
					try {
						component.exportProjectToFile(file.absolutePath(), options)
						component.showToast(Res.string.project_home_action_export_toast_success)
					} catch (e: CancellationException) {
						throw e
					} catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
						Napier.e("Story export failed", e)
						component.showToast(Res.string.project_home_action_export_toast_failure)
					}
				}
			} else {
				component.endProjectExport()
			}
		}
	}
}
