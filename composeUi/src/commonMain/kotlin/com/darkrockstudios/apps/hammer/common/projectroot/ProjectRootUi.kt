package com.darkrockstudios.apps.hammer.common.projectroot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SetScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.encyclopedia.BrowseEntriesFab
import com.darkrockstudios.apps.hammer.common.encyclopedia.EncyclopediaUi
import com.darkrockstudios.apps.hammer.common.globalsearch.GlobalSearchFab
import com.darkrockstudios.apps.hammer.common.globalsearch.GlobalSearchUi
import com.darkrockstudios.apps.hammer.common.notes.NotesFab
import com.darkrockstudios.apps.hammer.common.notes.NotesUi
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectHomeUi
import com.darkrockstudios.apps.hammer.common.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.reauthentication.ReauthenticationUi
import com.darkrockstudios.apps.hammer.common.storyeditor.StoryEditorUi
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineUi
import com.darkrockstudios.apps.hammer.common.timeline.TimelineFab
import org.jetbrains.compose.resources.vectorResource

private val WIDE_SCREEN_THRESHOLD = 650.dp

@Composable
fun getDestinationIcon(location: ProjectRoot.DestinationTypes): ImageVector {
	return when (location) {
		ProjectRoot.DestinationTypes.Editor -> vectorResource(Res.drawable.ic_editor)
		ProjectRoot.DestinationTypes.Notes -> vectorResource(Res.drawable.ic_notes)
		ProjectRoot.DestinationTypes.Encyclopedia -> vectorResource(Res.drawable.ic_encyclopedia)
		ProjectRoot.DestinationTypes.TimeLine -> vectorResource(Res.drawable.ic_timeline)
		ProjectRoot.DestinationTypes.Home -> vectorResource(Res.drawable.ic_home)
	}
}

@Composable
fun ProjectRootUi(
	component: ProjectRoot,
	rootSnackbar: RootSnackbarHostState,
	navWidth: Dp = Dp.Unspecified,
	modifier: Modifier = Modifier,
) {
	SetScreenCharacteristics(WIDE_SCREEN_THRESHOLD) {
		FeatureContent(modifier.fillMaxSize(), component, rootSnackbar, navWidth)
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
	navWidth: Dp = Dp.Unspecified,
) {
	val routerState by component.routerState.subscribeAsState()
	Children(
		modifier = modifier,
		stack = routerState,
		animation = predictiveBackAnimation(
			backHandler = component.backHandler,
			fallbackAnimation = stackAnimation { _ -> fade() },
			onBack = component::onBack,
		),
	) {
		when (val child = it.instance) {
			is ProjectRoot.Destination.EditorDestination ->
				StoryEditorUi(child.component, rootSnackbar, navWidth)

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
	when (val overlay = state.child?.instance) {
		null, ProjectRoot.ModalDestination.None -> {}
		is ProjectRoot.ModalDestination.ProjectSync -> {
			ProjectSynchronization(overlay.component, showSnackbar)
		}

		is ProjectRoot.ModalDestination.ServerReauth -> {
			ReauthenticationUi(overlay.component)
		}

		is ProjectRoot.ModalDestination.GlobalSearchModal -> {
			GlobalSearchUi(overlay.component)
		}
	}
}

@Composable
fun ProjectRootFab(
	component: ProjectRoot,
	modifier: Modifier = Modifier,
) {
	val routerState by component.routerState.subscribeAsState()
	val instance = routerState.active.instance

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.End,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		GlobalSearchFab(onClick = { component.showGlobalSearch() })

		when (instance) {
			is ProjectRoot.Destination.EditorDestination -> {}
			is ProjectRoot.Destination.NotesDestination -> NotesFab(instance.component, Modifier)
			is ProjectRoot.Destination.EncyclopediaDestination -> BrowseEntriesFab(instance.component, Modifier)
			is ProjectRoot.Destination.TimeLineDestination -> TimelineFab(instance.component, Modifier)
			is ProjectRoot.Destination.HomeDestination -> {}
		}
	}
}
