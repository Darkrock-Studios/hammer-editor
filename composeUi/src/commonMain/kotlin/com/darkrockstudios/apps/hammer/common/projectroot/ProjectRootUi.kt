package com.darkrockstudios.apps.hammer.common.projectroot

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.ScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.encyclopedia.EncyclopediaUi
import com.darkrockstudios.apps.hammer.common.notes.NotesUi
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorUi
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectHomeUi
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineUi
import com.darkrockstudios.apps.hammer.common.uiNeedsExplicitCloseButtons

private val VERTICAL_CONTROL_WIDTH_THRESHOLD = 700.dp

fun getDestinationIcon(location: ProjectRoot.DestinationTypes): ImageVector {
	return when (location) {
		ProjectRoot.DestinationTypes.Editor -> Icons.Filled.Edit
		ProjectRoot.DestinationTypes.Notes -> Icons.Filled.Dock
		ProjectRoot.DestinationTypes.Encyclopedia -> Icons.Filled.Dataset
		ProjectRoot.DestinationTypes.TimeLine -> Icons.Filled.CalendarMonth
		ProjectRoot.DestinationTypes.Home -> Icons.Filled.Home
	}
}

@Composable
fun ProjectRootUi(
	component: ProjectRoot,
	drawableKlass: Any? = null
) {
	BoxWithConstraints {
		val routerState by component.routerState.subscribeAsState()

		val isWide by remember(maxWidth) { derivedStateOf { maxWidth >= VERTICAL_CONTROL_WIDTH_THRESHOLD } }
		CompositionLocalProvider(
			LocalScreenCharacteristic provides ScreenCharacteristics(
				isWide,
				uiNeedsExplicitCloseButtons()
			)
		) {
			FeatureContent(Modifier.fillMaxSize(), routerState, isWide, drawableKlass)
		}
	}
}

@Composable
fun FeatureContent(
	modifier: Modifier,
	routerState: ChildStack<*, ProjectRoot.Destination<*>>,
	isWide: Boolean,
	drawableKlass: Any? = null
) {
	Children(
		modifier = modifier,
		stack = routerState,
		//animation = stackAnimation { _, _, _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectRoot.Destination.EditorDestination ->
				ProjectEditorUi(component = child.component, isWide = isWide, drawableKlass = drawableKlass)

			is ProjectRoot.Destination.NotesDestination ->
				NotesUi(child.component)

			is ProjectRoot.Destination.EncyclopediaDestination ->
				EncyclopediaUi(child.component)

			is ProjectRoot.Destination.TimeLineDestination ->
				TimeLineUi(child.component)

			is ProjectRoot.Destination.HomeDestination ->
				ProjectHomeUi(child.component)
		}
	}
}