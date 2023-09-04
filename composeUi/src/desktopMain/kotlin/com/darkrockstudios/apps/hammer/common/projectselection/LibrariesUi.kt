package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults

@Composable
actual fun LibrariesUi(
	showLibraries: Boolean,
	close: () -> Unit
) {
	val colors =
		LibraryDefaults.libraryColors(
			backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
			contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
			badgeBackgroundColor = MaterialTheme.colorScheme.primary,
			badgeContentColor = MaterialTheme.colorScheme.onPrimary,
		)

	SimpleDialog(
		onCloseRequest = close,
		visible = showLibraries,
		title = MR.strings.project_libraries_dialog_title.get(),
	) {
		val librariesInfo: LibraryInfoProvider = rememberKoinInject()
		LibrariesContainer(
			librariesBlock = librariesInfo::getLibs,
			modifier = Modifier.fillMaxSize(),
			colors = colors
		)
	}
}