package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

object Ui {
	object Elevation {
		val SMALL = 4.dp
		val MEDIUM = 8.dp
		val LARGE = 12.dp
	}

	object ToneElevation {
		val NONE = 0.dp
		val SMALL = 1.dp
		val MEDIUM = 2.dp
		val LARGE = 4.dp
	}

	object Padding {
		val S = 4.dp
		val M = 8.dp
		val L = 12.dp
		val XL = 16.dp

		val contents = PaddingValues(
			top = XL,
			start = XL,
			end = XL,
		)
	}

	object NavDrawer {
		val widthExpanded = 240.dp
	}

	val MIN_TOUCH_SIZE = 48.dp
	val TOP_BAR_HEIGHT = 56.dp
}