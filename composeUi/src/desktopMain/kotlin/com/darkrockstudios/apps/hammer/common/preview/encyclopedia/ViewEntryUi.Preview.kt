package com.darkrockstudios.apps.hammer.common.preview.encyclopedia

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.encyclopedia.ViewEntryUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeViewEntryComponent


@Preview
@Composable
fun ViewEntryUiPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		ViewEntryUi(
			component = fakeViewEntryComponent,
			scope = scope,
			rootSnackbar = rememberRootSnackbarHostState(),
			closeEntry = {},
		)
	}
}