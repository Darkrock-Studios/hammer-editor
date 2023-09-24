package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults.libraryColors

@Composable
actual fun LibrariesUi(
	showLibraries: Boolean,
	close: () -> Unit
) {
	val colors =
		libraryColors(
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
		val libraryInfo: LibraryInfoProvider = rememberKoinInject()
		Box(modifier = Modifier.fillMaxSize()) {
			LibrariesContainer(
				modifier = Modifier
					.fillMaxSize()
					.height(500.dp),
				librariesBlock = { _ ->
					val lib = libraryInfo.getLibs()
					lib
				},
				colors = colors
			)
		}
	}
}