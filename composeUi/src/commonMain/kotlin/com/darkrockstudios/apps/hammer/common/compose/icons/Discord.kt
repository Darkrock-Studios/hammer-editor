package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AboutIcons.Discord: ImageVector
	get() {
		if (_Discord != null) {
			return _Discord!!
		}
		_Discord = ImageVector.Builder(
			name = "Discord",
			defaultWidth = 640.dp,
			defaultHeight = 512.dp,
			viewportWidth = 640f,
			viewportHeight = 512f
		).apply {
			path(fill = SolidColor(Color.Black)) {
				moveTo(524.53f, 69.84f)
				arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.76f, -0.7f)
				arcTo(485.07f, 485.07f, 0f, isMoreThanHalf = false, isPositiveArc = false, 404.08f, 32.03f)
				arcToRelative(1.82f, 1.82f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.92f, 0.91f)
				arcToRelative(337.46f, 337.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -14.9f, 30.6f)
				arcToRelative(447.85f, 447.85f, 0f, isMoreThanHalf = false, isPositiveArc = false, -134.43f, 0f)
				arcToRelative(309.54f, 309.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, -15.14f, -30.6f)
				arcToRelative(1.89f, 1.89f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.92f, -0.91f)
				arcTo(483.69f, 483.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, 116.08f, 69.14f)
				arcToRelative(1.71f, 1.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.79f, 0.68f)
				curveTo(39.07f, 183.65f, 18.19f, 294.69f, 28.43f, 404.35f)
				arcToRelative(2.02f, 2.02f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.76f, 1.38f)
				arcTo(487.67f, 487.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 176.02f, 479.92f)
				arcToRelative(1.9f, 1.9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.06f, -0.68f)
				arcTo(348.2f, 348.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 208.12f, 430.4f)
				arcToRelative(1.86f, 1.86f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.02f, -2.59f)
				arcToRelative(321.17f, 321.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, -45.87f, -21.85f)
				arcToRelative(1.88f, 1.88f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, -3.13f)
				curveToRelative(3.08f, -2.31f, 6.17f, -4.71f, 9.11f, -7.14f)
				arcToRelative(1.82f, 1.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.9f, -0.26f)
				curveToRelative(96.23f, 43.92f, 200.41f, 43.92f, 295.5f, 0f)
				arcToRelative(1.81f, 1.81f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.92f, 0.23f)
				curveToRelative(2.94f, 2.43f, 6.03f, 4.85f, 9.13f, 7.16f)
				arcToRelative(1.88f, 1.88f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, 3.13f)
				arcToRelative(301.41f, 301.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, -45.89f, 21.83f)
				arcToRelative(1.88f, 1.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1f, 2.61f)
				arcToRelative(391.05f, 391.05f, 0f, isMoreThanHalf = false, isPositiveArc = false, 30.01f, 48.81f)
				arcToRelative(1.86f, 1.86f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.06f, 0.7f)
				arcTo(486.05f, 486.05f, 0f, isMoreThanHalf = false, isPositiveArc = false, 610.7f, 405.73f)
				arcToRelative(1.88f, 1.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.76f, -1.35f)
				curveTo(623.73f, 277.59f, 590.93f, 167.46f, 524.53f, 69.84f)
				close()
				moveTo(222.49f, 337.58f)
				curveToRelative(-28.97f, 0f, -52.84f, -26.59f, -52.84f, -59.24f)
				reflectiveCurveTo(193.06f, 219.1f, 222.49f, 219.1f)
				curveToRelative(29.67f, 0f, 53.31f, 26.82f, 52.84f, 59.24f)
				curveTo(275.33f, 310.99f, 251.92f, 337.58f, 222.49f, 337.58f)
				close()
				moveTo(417.87f, 337.58f)
				curveToRelative(-28.97f, 0f, -52.84f, -26.59f, -52.84f, -59.24f)
				reflectiveCurveTo(388.44f, 219.1f, 417.87f, 219.1f)
				curveToRelative(29.67f, 0f, 53.31f, 26.82f, 52.84f, 59.24f)
				curveTo(470.71f, 310.99f, 447.54f, 337.58f, 417.87f, 337.58f)
				close()
			}
		}.build()

		return _Discord!!
	}

@Suppress("ObjectPropertyName")
private var _Discord: ImageVector? = null
