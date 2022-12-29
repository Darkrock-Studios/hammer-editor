package com.darkrockstudios.apps.hammer.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.darkrockstudios.apps.hammer.common.notes.NotesUi
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorUi
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRoot

private val VERTICAL_CONTROL_WIDTH_THRESHOLD = 800.dp

@Composable
fun ProjectRootUi(
	component: ProjectRoot,
	padding: PaddingValues = PaddingValues.Absolute(),
	drawableKlass: Any? = null
) {
	BoxWithConstraints(Modifier.padding(padding)) {
		val routerState by component.routerState.subscribeAsState()

		val isWide = this.maxWidth >= VERTICAL_CONTROL_WIDTH_THRESHOLD
		if (isWide) {
			Row(modifier = Modifier.fillMaxSize()) {
				Column(Modifier.wrapContentWidth().fillMaxHeight()) {
					FeatureTabs(component)
				}
				FeatureContent(Modifier.fillMaxSize(), routerState, isWide, drawableKlass)
			}
		} else {
			Column(modifier = Modifier.fillMaxSize()) {
				Row(modifier = Modifier.fillMaxWidth().background(Color.Cyan)) {
					FeatureTabs(component)
				}
				FeatureContent(Modifier.fillMaxSize(), routerState, isWide, drawableKlass)
			}
		}
	}
}

@Composable
fun FeatureTabs(component: ProjectRoot) {
	Button(onClick = { component.showEditor() }) {
		Text("Editor")
	}
	Button(onClick = { component.showNotes() }) {
		Text("Notes")
	}
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun FeatureContent(
	modifier: Modifier,
	routerState: ChildStack<*, ProjectRoot.Destination>,
	isWide: Boolean,
	drawableKlass: Any? = null
) {
	Children(
		modifier = modifier,
		stack = routerState,
		animation = stackAnimation { _, _, _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectRoot.Destination.EditorDestination ->
				ProjectEditorUi(component = child.component, isWide = isWide, drawableKlass = drawableKlass)

			is ProjectRoot.Destination.NotesDestination ->
				NotesUi(child.component)
		}
	}
}