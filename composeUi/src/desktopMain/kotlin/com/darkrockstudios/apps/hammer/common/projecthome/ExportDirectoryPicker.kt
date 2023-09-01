package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
	snackbarHostState: SnackbarHostState,
) {
	val strRes = rememberStrRes()
	val defaultDispatcher = rememberDefaultDispatcher()

	DirectoryPicker(show) { path ->
		if (path != null) {
			scope.launch(defaultDispatcher) {
				component.exportProject(path)
				snackbarHostState.showSnackbar(strRes.get(MR.strings.project_home_action_export_toast_success))
			}
		} else {
			component.endProjectExport()
		}
	}
}
