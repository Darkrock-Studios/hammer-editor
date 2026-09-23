package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

// Items shrink and fade toward the top and bottom of the screen so a round bezel never clips them.

/** Pair with `transformation = SurfaceTransformation(spec)`. */
@Composable
fun Modifier.listButton(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
	listSurface(scope, spec, ButtonDefaults.minimumVerticalListContentPadding)

/** Pair with `transformation = SurfaceTransformation(spec)`. */
@Composable
fun Modifier.listHeader(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
	listSurface(scope, spec, ListHeaderDefaults.minimumTopListContentPadding)

private fun Modifier.listSurface(
	scope: TransformingLazyColumnItemScope,
	spec: TransformationSpec,
	minimumVerticalPadding: Dp,
): Modifier = with(scope) {
	fillMaxWidth()
		.transformedHeight(scope, spec)
		.minimumVerticalContentPadding(minimumVerticalPadding)
}

// Same inset a ListHeader gives its content. Text has no pill to morph, so at full width its
// outer lines reach the bezel near the top and bottom before the scale shrinks them enough.
private val TextHorizontalPadding = 14.dp

@Composable
fun Modifier.listText(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
	listContent(scope, spec).fillMaxWidth().padding(horizontal = TextHorizontalPadding)

/** For content with no `transformation` parameter of its own. */
@Composable
fun Modifier.listContent(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier {
	val transformed = remember(scope, spec) {
		val transformation = scope.SurfaceTransformation(spec)
		Modifier
			.transformedHeight(scope, spec)
			.graphicsLayer { with(transformation) { applyContainerTransformation() } }
	}
	return then(transformed)
}
