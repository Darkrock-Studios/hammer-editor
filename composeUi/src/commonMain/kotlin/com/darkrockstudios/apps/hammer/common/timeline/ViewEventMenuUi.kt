package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent

@Composable
expect fun ViewEventMenuUi(component: ViewTimeLineEvent)