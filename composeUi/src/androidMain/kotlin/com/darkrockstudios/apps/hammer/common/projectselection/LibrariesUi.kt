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
import com.mikepenz.aboutlibraries.ui.compose.DefaultChipColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors

@Composable
actual fun LibrariesUi(
	showLibraries: Boolean,
	close: () -> Unit
) {
	val colors = LibraryDefaults.libraryColors(
		libraryBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
		libraryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
		licenseChipColors = DefaultChipColors(
			containerColor = MaterialTheme.colorScheme.primary,
			contentColor = MaterialTheme.colorScheme.onPrimary,
		),
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
				libraries = libraryInfo.getLibs(),
				colors = colors
			)
		}
	}
}