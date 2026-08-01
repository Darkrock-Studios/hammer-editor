package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Whether there is anywhere left to scroll in either direction. */
val ScrollableState.isScrollable: Boolean
	get() = canScrollForward || canScrollBackward

/**
 * Placement for a scrollbar overlaid on a scrolling sibling: full height, pinned to the trailing
 * edge. Uses [BoxScope.matchParentSize] rather than `fillMaxHeight` so the bar never contributes to
 * the parent's size, which matters in the wrap-content boxes the detail screens use.
 */
@Suppress("ModifierFactoryExtensionFunction")
fun BoxScope.scrollBarOverlay(): Modifier =
	Modifier
		.matchParentSize()
		.wrapContentWidth(Alignment.End)
