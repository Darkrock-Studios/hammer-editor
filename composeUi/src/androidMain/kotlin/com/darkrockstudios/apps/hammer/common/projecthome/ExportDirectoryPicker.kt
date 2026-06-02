package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
actual fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
) {
	val ioDispatcher = rememberIoDispatcher()
	val externalFileIo: ExternalFileIo = rememberKoinInject()
	val state by component.state.subscribeAsState()
	val format = state.exportOptions.format
	val launcher = rememberLauncherForActivityResult(
		remember(format) { ActivityResultContracts.CreateDocument(mimeTypeFor(format)) }
	) { uri ->
		if (uri != null) {
			val options = state.exportOptions
			scope.launch(ioDispatcher) {
				val exportTempFile = getCacheDirectory()
				val tempFilePath = component.exportProject(exportTempFile, options)
				val tempFile = File(tempFilePath.path)
				val bytes = tempFile.readBytes()
				tempFile.delete()

				val ok = externalFileIo.writeExternalFile(
					path = uri.toString(),
					content = bytes,
				)
				val toast = if (ok) {
					Res.string.project_home_action_export_toast_success
				} else {
					Res.string.project_home_action_export_toast_failure
				}
				component.showToast(toast)
			}
		} else {
			component.endProjectExport()
		}
	}

	LaunchedEffect(show) {
		if (show) {
			launcher.launch(component.getExportStoryFileName(format))
		}
	}
}

private fun mimeTypeFor(format: ExportFormat): String = when (format) {
	// text/markdown is missing from MimeTypeMap on many Android versions / SAF providers; text/plain is universal and the
	// extension on the suggested filename ("$projectName.md") is what determines association.
	ExportFormat.Markdown -> "text/plain"
	ExportFormat.Epub -> "application/epub+zip"
}