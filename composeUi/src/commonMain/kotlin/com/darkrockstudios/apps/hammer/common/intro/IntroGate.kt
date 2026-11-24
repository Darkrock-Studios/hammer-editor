package com.darkrockstudios.apps.hammer.common.intro

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection.IntroStage

/**
 * Shows the first-run intro in place of [content] while it is up, and a blank page while that is
 * being decided. Only once the intro has shown does leaving it crossfade; until then the screen
 * goes straight to [content].
 */
@Composable
fun IntroGate(component: ProjectSelection, content: @Composable () -> Unit) {
	val stage by component.introStage.subscribeAsState()
	var introSeen by remember { mutableStateOf(false) }
	if (stage is IntroStage.Showing) SideEffect { introSeen = true }

	if (introSeen || stage is IntroStage.Showing) {
		Crossfade(targetState = stage as? IntroStage.Showing, label = "introGate") { showing ->
			if (showing != null) IntroUi(showing.intro) else content()
		}
	} else if (stage == IntroStage.Deciding) {
		Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
	} else {
		content()
	}
}
