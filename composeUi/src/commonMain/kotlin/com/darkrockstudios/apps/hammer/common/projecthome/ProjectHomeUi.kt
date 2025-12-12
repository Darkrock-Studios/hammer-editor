package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
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
	ChildStack(
		stack = contentState,
		modifier = Modifier.fillMaxSize(),
		animation = stackAnimation(
			animator = fade(),
			predictiveBackParams = {
				PredictiveBackParams(
					backHandler = component.backHandler,
					onBack = component::onBack,
				)
			},
		),
	) { child ->
		when (val dest = child.instance) {
			is ProjectHome.ContentDestination.Stats -> {
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
					component = dest.component,
					onClose = component::showProjectStats
				)
			}
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
