package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
actual fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
	snackbarHostState: SnackbarHostState,
) {
	val strRes = rememberStrRes()
	val ioDispatcher = rememberIoDispatcher()
	val externalFileIo: ExternalFileIo = rememberKoinInject()
	val launcher =
		rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
			if (uri != null) {
				scope.launch(ioDispatcher) {
					val exportTempFile = getCacheDirectory()
					val tempFilePath = component.exportProject(exportTempFile)
					val tempFile = File(tempFilePath.path)
					val content = tempFile.readText()
					tempFile.delete()

					externalFileIo.writeExternalFile(
						path = uri.toString(),
						content = content
					)
					snackbarHostState.showSnackbar(strRes.get(MR.strings.project_home_action_export_toast_success))
				}
			} else {
				component.endProjectExport()
			}
		}

	LaunchedEffect(show) {
		if (show) {
			launcher.launch(component.getExportStoryFileName())
		}
	}
}