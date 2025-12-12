package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AboutIcons.Reddit: ImageVector
	get() {
		if (_Reddit != null) {
			return _Reddit!!
		}
		_Reddit = ImageVector.Builder(
			name = "Reddit",
			defaultWidth = 512.dp,
			defaultHeight = 512.dp,
			viewportWidth = 512f,
			viewportHeight = 512f
		).apply {
			path(fill = SolidColor(Color.Black)) {
				moveTo(440.3f, 203.5f)
				curveToRelative(-15f, 0f, -28.2f, 6.2f, -37.9f, 15.9f)
				curveToRelative(-35.7f, -24.7f, -83.8f, -40.6f, -137.1f, -42.3f)
				lineTo(293f, 52.3f)
				lineToRelative(88.2f, 19.8f)
				curveToRelative(0f, 21.6f, 17.6f, 39.2f, 39.2f, 39.2f)
				curveToRelative(22f, 0f, 39.7f, -18.1f, 39.7f, -39.7f)
				reflectiveCurveToRelative(-17.6f, -39.7f, -39.7f, -39.7f)
				curveToRelative(-15.4f, 0f, -28.7f, 9.3f, -35.3f, 22f)
				lineToRelative(-97.4f, -21.6f)
				curveToRelative(-4.9f, -1.3f, -9.7f, 2.2f, -11f, 7.1f)
				lineTo(246.3f, 177f)
				curveToRelative(-52.9f, 2.2f, -100.5f, 18.1f, -136.3f, 42.8f)
				curveToRelative(-9.7f, -10.1f, -23.4f, -16.3f, -38.4f, -16.3f)
				curveToRelative(-55.6f, 0f, -73.8f, 74.6f, -22.9f, 100.1f)
				curveToRelative(-1.8f, 7.9f, -2.6f, 16.3f, -2.6f, 24.7f)
				curveToRelative(0f, 83.8f, 94.4f, 151.7f, 210.3f, 151.7f)
				curveToRelative(116.4f, 0f, 210.8f, -67.9f, 210.8f, -151.7f)
				curveToRelative(0f, -8.4f, -0.9f, -17.2f, -3.1f, -25.1f)
				curveToRelative(49.9f, -25.6f, 31.5f, -99.7f, -23.8f, -99.7f)
				close()
				moveTo(129.4f, 308.9f)
				curveToRelative(0f, -22f, 17.6f, -39.7f, 39.7f, -39.7f)
				curveToRelative(21.6f, 0f, 39.2f, 17.6f, 39.2f, 39.7f)
				curveToRelative(0f, 21.6f, -17.6f, 39.2f, -39.2f, 39.2f)
				curveToRelative(-22f, 0.1f, -39.7f, -17.6f, -39.7f, -39.2f)
				close()
				moveTo(343.7f, 402.4f)
				curveToRelative(-36.4f, 36.4f, -139.1f, 36.4f, -175.5f, 0f)
				curveToRelative(-4f, -3.5f, -4f, -9.7f, 0f, -13.7f)
				curveToRelative(3.5f, -3.5f, 9.7f, -3.5f, 13.2f, 0f)
				curveToRelative(27.8f, 28.5f, 120f, 29f, 149f, 0f)
				curveToRelative(3.5f, -3.5f, 9.7f, -3.5f, 13.2f, 0f)
				curveToRelative(4.1f, 4f, 4.1f, 10.2f, 0.1f, 13.7f)
				close()
				moveTo(342.9f, 348.2f)
				curveToRelative(-21.6f, 0f, -39.2f, -17.6f, -39.2f, -39.2f)
				curveToRelative(0f, -22f, 17.6f, -39.7f, 39.2f, -39.7f)
				curveToRelative(22f, 0f, 39.7f, 17.6f, 39.7f, 39.7f)
				curveToRelative(-0.1f, 21.5f, -17.7f, 39.2f, -39.7f, 39.2f)
				close()
			}
		}.build()

		return _Reddit!!
	}

@Suppress("ObjectPropertyName")
private var _Reddit: ImageVector? = null
