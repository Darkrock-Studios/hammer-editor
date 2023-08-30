package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer

@Composable
actual fun LibrariesUi(
	showLibraries: Boolean,
	close: () -> Unit
) {
	SimpleDialog(
		onCloseRequest = close,
		visible = showLibraries,
		title = MR.strings.project_libraries_dialog_title.get(),
	) {
		val librariesInfo: LibraryInfoProvider = rememberKoinInject()
		LibrariesContainer(
			librariesBlock = librariesInfo::getLibs,
			modifier = Modifier.fillMaxSize()
		)
	}
}