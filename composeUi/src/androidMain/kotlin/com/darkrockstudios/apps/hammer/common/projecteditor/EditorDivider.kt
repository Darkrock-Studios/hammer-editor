package com.darkrockstudios.apps.hammer.common.projecteditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

@Composable
actual fun rememberEditorDivider(): EditorDivider {
	val density = LocalDensity.current
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	val context = LocalContext.current
	val foldingState by produceState<EditorDivider>(
		initialValue = EditorDivider()
	) {
		lifecycle.repeatOnLifecycle(state = Lifecycle.State.STARTED) {
			WindowInfoTracker.getOrCreate(context)
				.windowLayoutInfo(context)
				.collect { layoutInfo ->
					val foldingFeature = layoutInfo.displayFeatures.find { it is FoldingFeature }
					value = if (foldingFeature is FoldingFeature
						&& foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL
					) {
						EditorDivider(
							x = density.run { foldingFeature.bounds.centerX().toDp() }
						)
					} else {
						EditorDivider()
					}
				}
		}
	}

	return foldingState
}