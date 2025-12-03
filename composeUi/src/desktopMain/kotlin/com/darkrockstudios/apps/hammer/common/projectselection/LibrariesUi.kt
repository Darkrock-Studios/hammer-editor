package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.mikepenz.aboutlibraries.ui.compose.DefaultChipColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors


@OptIn(ExperimentalMaterialApi::class)
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
		val librariesInfo: LibraryInfoProvider = rememberKoinInject()
		LibrariesContainer(
			libraries = librariesInfo.getLibs(),
			modifier = Modifier.fillMaxSize(),
			colors = colors
		)
	}
}