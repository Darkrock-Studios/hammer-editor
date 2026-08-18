package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_success
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
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
	// Snapshot of the confirmed options taken when the SAF picker launches; the
	// picker does not block the app, so options must not be re-read afterwards.
	var confirmedOptions by remember { mutableStateOf<ExportOptions?>(null) }
	val launcher = rememberLauncherForActivityResult(
		remember(format) { ActivityResultContracts.CreateDocument(mimeTypeFor(format)) }
	) { uri ->
		val options = confirmedOptions
		if (uri != null && options != null) {
			scope.launch(ioDispatcher) {
				// A renderer failure must surface as a toast; an uncaught throw here takes down the app.
				try {
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

	LaunchedEffect(show) {
		if (show) {
			val options = component.state.value.exportOptions
			confirmedOptions = options
			launcher.launch(component.getExportStoryFileName(options.format))
		}
	}
}

private fun mimeTypeFor(format: ExportFormat): String = when (format) {
	// text/markdown is missing from MimeTypeMap on many Android versions / SAF providers; text/plain is universal and the
	// extension on the suggested filename ("$projectName.md") is what determines association.
	ExportFormat.Markdown -> "text/plain"
	ExportFormat.Epub -> "application/epub+zip"
	ExportFormat.Pdf -> "application/pdf"
	ExportFormat.Docx -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
	ExportFormat.Rtf -> "application/rtf"
}