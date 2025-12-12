package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_success
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
) {
	val strRes = rememberStrRes()
	val defaultDispatcher = rememberDefaultDispatcher()

	val directoryPickerLauncher = rememberDirectoryPickerLauncher { directory ->
		if (directory != null) {
			scope.launch(defaultDispatcher) {
				component.exportProject(directory.absolutePath())
				rootSnackbar.showSnackbar(strRes.get(Res.string.project_home_action_export_toast_success))
			}
		} else {
			component.endProjectExport()
		}
	}

	LaunchedEffect(show) {
		if (show) {
			directoryPickerLauncher.launch()
		}
	}
}
