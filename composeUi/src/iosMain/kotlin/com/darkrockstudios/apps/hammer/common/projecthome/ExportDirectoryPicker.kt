package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_export_toast_success
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
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
	// Snapshot of the confirmed options taken when the picker launches; the picker
	// does not block the app, so options must not be re-read afterwards.
	var confirmedOptions by remember { mutableStateOf<ExportOptions?>(null) }

	val directoryPickerLauncher = rememberDirectoryPickerLauncher { directory ->
		val options = confirmedOptions
		if (directory != null && options != null) {
			scope.launch(defaultDispatcher) {
				// FileKit's absolutePath() returns nsUrl.absoluteString (with "file://" scheme), which okio's
				// posix sink can't open — use .path for the bare filesystem path. UIDocumentPicker URLs are
				// security-scoped, so writes outside the sandbox require start/stopAccessingSecurityScopedResource.
				val accessGranted = directory.startAccessingSecurityScopedResource()
				// A renderer failure must surface as a toast; an uncaught throw here takes down the app.
				try {
					component.exportProject(directory.path, options)
					component.showToast(Res.string.project_home_action_export_toast_success)
				} catch (e: CancellationException) {
					throw e
				} catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
					Napier.e("Story export failed", e)
					component.showToast(Res.string.project_home_action_export_toast_failure)
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
			confirmedOptions = component.state.value.exportOptions
			directoryPickerLauncher.launch()
		}
	}
}
