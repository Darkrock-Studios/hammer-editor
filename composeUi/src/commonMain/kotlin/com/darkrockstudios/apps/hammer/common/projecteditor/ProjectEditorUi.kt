package com.darkrockstudios.apps.hammer.common.projecteditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.animation.child.childAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.animation.child.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.RouterState
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListUi

private val MULTI_PANE_WIDTH_THRESHOLD = 800.dp
private const val LIST_PANE_WEIGHT = 0.4F
private const val DETAILS_PANE_WEIGHT = 0.6F

@Composable
fun ProjectEditorUi(
    component: ProjectEditorComponent,
    modifier: Modifier = Modifier,
    drawableKlass: Any? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val state by component.state.subscribeAsState()
        val isMultiPane = state.isMultiPane

        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                routerState = component.listRouterState,
                modifier = Modifier.weight(if (isMultiPane) LIST_PANE_WEIGHT else 1F),
            )

            if (isMultiPane) {
                Box(modifier = Modifier.weight(DETAILS_PANE_WEIGHT))
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (isMultiPane) {
                Box(modifier = Modifier.weight(LIST_PANE_WEIGHT))
            }

            DetailsPane(
                routerState = component.detailsRouterState,
                modifier = Modifier.weight(if (isMultiPane) DETAILS_PANE_WEIGHT else 1F),
                drawableKlass = drawableKlass
            )
        }

        val isMultiPaneRequired = this@BoxWithConstraints.maxWidth >= MULTI_PANE_WIDTH_THRESHOLD
        LaunchedEffect(isMultiPaneRequired) {
            component.setMultiPane(isMultiPaneRequired)
        }
    }
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
private fun ListPane(
    routerState: Value<RouterState<*, ProjectEditor.Child.List>>,
    modifier: Modifier
) {
    Children(
        routerState = routerState,
        modifier = modifier,
        animation = childAnimation(fade()),
    ) {
        when (val child = it.instance) {
            is ProjectEditor.Child.List.Scenes ->
                SceneListUi(
                    component = child.component,
                    modifier = Modifier.fillMaxSize()
                )
            is ProjectEditor.Child.List.None -> Box {}
        }
    }
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
private fun DetailsPane(
    routerState: Value<RouterState<*, ProjectEditor.Child.Detail>>,
    modifier: Modifier,
    drawableKlass: Any? = null
) {
    Children(
        routerState = routerState,
        modifier = modifier,
        animation = childAnimation(fade()),
    ) {
        when (val child = it.instance) {
            is ProjectEditor.Child.Detail.None -> Box {}
            is ProjectEditor.Child.Detail.Editor -> {
                SceneEditorUi(
                    component = child.component,
                    modifier = Modifier.fillMaxSize(),
                    drawableKlass = drawableKlass
                )
            }
        }
    }
}