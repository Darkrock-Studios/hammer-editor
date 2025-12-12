package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import kotlinx.coroutines.CoroutineScope

@Composable
fun ProjectHomeUi(
	component: ProjectHome,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val contentState by component.contentRouterState.subscribeAsState()

	when (val content = contentState.child?.instance) {
		null, is ProjectHome.ContentDestination.Stats -> {
			ProjectStatsUi(
				modifier = modifier,
				component = component,
				scope = scope,
				rootSnackbar = rootSnackbar
			)
		}

		is ProjectHome.ContentDestination.ProjectSettings -> {
			ProjectSettingsUi(
				modifier = modifier,
				component = content.component,
				onClose = component::showProjectStats
			)
		}
	}
}

@Composable
expect fun ExportDirectoryPicker(
	show: Boolean,
	component: ProjectHome,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
)
