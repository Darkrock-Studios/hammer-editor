package com.darkrockstudios.apps.hammer.common.projectroot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.SetScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.encyclopedia.BrowseEntriesFab
import com.darkrockstudios.apps.hammer.common.encyclopedia.EncyclopediaUi
import com.darkrockstudios.apps.hammer.common.notes.NotesFab
import com.darkrockstudios.apps.hammer.common.notes.NotesUi
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorUi
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectHomeUi
import com.darkrockstudios.apps.hammer.common.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineUi
import com.darkrockstudios.apps.hammer.common.timeline.TimelineFab
import kotlinx.coroutines.launch

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
	val scope = rememberCoroutineScope()
	val snackbarState = remember { SnackbarHostState() }
	SetScreenCharacteristics(WIDE_SCREEN_THRESHOLD) {
		Box {
			FeatureContent(modifier.fillMaxSize(), component, snackbarState)
			SnackbarHost(
				snackbarState,
				modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
			)
		}
	}

	ModalContent(component) { message ->
		scope.launch { snackbarState.showSnackbar(message) }
	}
}

@Composable
fun FeatureContent(
	modifier: Modifier,
	component: ProjectRoot,
	snackbarState: SnackbarHostState,
) {
	val routerState by component.routerState.subscribeAsState()
	Children(
		modifier = modifier,
		stack = routerState,
		animation = stackAnimation { _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectRoot.Destination.EditorDestination ->
				ProjectEditorUi(child.component, snackbarState)

			is ProjectRoot.Destination.NotesDestination ->
				NotesUi(child.component, snackbarState)

			is ProjectRoot.Destination.EncyclopediaDestination ->
				EncyclopediaUi(child.component, snackbarState)

			is ProjectRoot.Destination.TimeLineDestination ->
				TimeLineUi(child.component, snackbarState)

			is ProjectRoot.Destination.HomeDestination ->
				ProjectHomeUi(child.component, snackbarState)
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
