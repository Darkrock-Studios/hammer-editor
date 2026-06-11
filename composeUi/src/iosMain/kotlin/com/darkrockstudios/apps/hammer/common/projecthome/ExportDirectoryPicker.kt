package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_success
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
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

	val directoryPickerLauncher = rememberDirectoryPickerLauncher { directory ->
		if (directory != null) {
			val options = state.exportOptions
			scope.launch(defaultDispatcher) {
				// FileKit's absolutePath() returns nsUrl.absoluteString (with "file://" scheme), which okio's
				// posix sink can't open — use .path for the bare filesystem path. UIDocumentPicker URLs are
				// security-scoped, so writes outside the sandbox require start/stopAccessingSecurityScopedResource.
				val accessGranted = directory.startAccessingSecurityScopedResource()
				try {
					component.exportProject(directory.path, options)
					component.showToast(Res.string.project_home_action_export_toast_success)
				} finally {
					if (accessGranted) directory.stopAccessingSecurityScopedResource()
				}
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
