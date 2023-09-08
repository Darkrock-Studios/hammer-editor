package com.darkrockstudios.apps.hammer.common.projectroot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SetScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.encyclopedia.BrowseEntriesFab
import com.darkrockstudios.apps.hammer.common.encyclopedia.EncyclopediaUi
import com.darkrockstudios.apps.hammer.common.notes.NotesFab
import com.darkrockstudios.apps.hammer.common.notes.NotesUi
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorUi
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectHomeUi
import com.darkrockstudios.apps.hammer.common.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineUi
import com.darkrockstudios.apps.hammer.common.timeline.TimelineFab

private val WIDE_SCREEN_THRESHOLD = 700.dp

fun getDestinationIcon(location: ProjectRoot.DestinationTypes): ImageVector {
	return when (location) {
		ProjectRoot.DestinationTypes.Editor -> Icons.Filled.Edit
		ProjectRoot.DestinationTypes.Notes -> Icons.Filled.Notes
		ProjectRoot.DestinationTypes.Encyclopedia -> Icons.Filled.Dataset
		ProjectRoot.DestinationTypes.TimeLine -> Icons.Filled.CalendarMonth
		ProjectRoot.DestinationTypes.Home -> Icons.Filled.Home
	}
}

@Composable
fun ProjectRootUi(
	component: ProjectRoot,
	modifier: Modifier = Modifier,
) {
	val rootSnackbar = rememberRootSnackbarHostState()
	SetScreenCharacteristics(WIDE_SCREEN_THRESHOLD) {
		Box {
			FeatureContent(modifier.fillMaxSize(), component, rootSnackbar)
			SnackbarHost(
				rootSnackbar.snackbarHostState,
				modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
			)
		}
	}

	ModalContent(component) { message ->
		rootSnackbar.showSnackbar(message)
	}
}

@Composable
fun FeatureContent(
	modifier: Modifier,
	component: ProjectRoot,
	rootSnackbar: RootSnackbarHostState,
) {
	val routerState by component.routerState.subscribeAsState()
	Children(
		modifier = modifier,
		stack = routerState,
		animation = stackAnimation { _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectRoot.Destination.EditorDestination ->
				ProjectEditorUi(child.component, rootSnackbar)

			is ProjectRoot.Destination.NotesDestination ->
				NotesUi(child.component, rootSnackbar)

			is ProjectRoot.Destination.EncyclopediaDestination ->
				EncyclopediaUi(child.component, rootSnackbar)

			is ProjectRoot.Destination.TimeLineDestination ->
				TimeLineUi(child.component, rootSnackbar)

			is ProjectRoot.Destination.HomeDestination ->
				ProjectHomeUi(child.component, rootSnackbar)
		}
	}
}

@Composable
fun ModalContent(component: ProjectRoot, showSnackbar: (String) -> Unit) {
	val state by component.modalRouterState.subscribeAsState()
	val overlay = state.child?.instance
	when (overlay) {
		ProjectRoot.ModalDestination.None -> {}
		is ProjectRoot.ModalDestination.ProjectSync -> {
			ProjectSynchronization(overlay.component, showSnackbar)
		}

		null -> {}
	}
}

@Composable
fun ProjectRootFab(
	component: ProjectRoot,
	modifier: Modifier = Modifier,
) {
	val routerState by component.routerState.subscribeAsState()

	/*
	AnimatedContent(
		targetState = routerState.active.instance,
		transitionSpec = {
			scaleIn(animationSpec = tween(500)) with
				scaleOut(animationSpec = tween(500))
		}
	) { instance ->
		*/
	val instance = routerState.active.instance
	when (instance) {
		is ProjectRoot.Destination.EditorDestination -> {

		}

		is ProjectRoot.Destination.NotesDestination -> {
			NotesFab(instance.component, modifier)
		}

		is ProjectRoot.Destination.EncyclopediaDestination -> {
			BrowseEntriesFab(instance.component, modifier)
		}

		is ProjectRoot.Destination.TimeLineDestination -> {
			TimelineFab(instance.component, modifier)
		}

		is ProjectRoot.Destination.HomeDestination -> {

		}
	}
}
