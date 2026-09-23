package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
	with(scope) {
		this@listButton
			.fillMaxWidth()
			.transformedHeight(scope, spec)
			.minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
	}

@Composable
fun Modifier.listHeader(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
	with(scope) {
		this@listHeader
			.listText(scope, spec)
			.minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding)
	}

fun Modifier.listText(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
	listContent(scope, spec).fillMaxWidth()

/** For content with no `transformation` parameter of its own. */
fun Modifier.listContent(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier {
	val transformation = with(scope) { SurfaceTransformation(spec) }
	return transformedHeight(scope, spec)
		.graphicsLayer { with(transformation) { applyContainerTransformation() } }
}
