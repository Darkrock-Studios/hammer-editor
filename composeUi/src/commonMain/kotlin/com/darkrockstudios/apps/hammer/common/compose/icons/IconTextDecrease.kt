package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val EditorIcons.IconTextDecrease: ImageVector
	get() {
		if (_IconTextDecrease != null) {
			return _IconTextDecrease!!
		}
		_IconTextDecrease = ImageVector.Builder(
			name = "IconTextDecrease",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 960f,
			viewportHeight = 960f
		).apply {
			path(fill = SolidColor(Color.Black)) {
				moveToRelative(40f, 760f)
				lineToRelative(210f, -560f)
				horizontalLineToRelative(100f)
				lineToRelative(210f, 560f)
				horizontalLineToRelative(-96f)
				lineToRelative(-51f, -143f)
				lineTo(187f, 617f)
				lineToRelative(-51f, 143f)
				lineTo(40f, 760f)
				close()
				moveTo(216f, 536f)
				horizontalLineToRelative(168f)
				lineToRelative(-82f, -232f)
				horizontalLineToRelative(-4f)
				lineToRelative(-82f, 232f)
				close()
				moveTo(600f, 520f)
				verticalLineToRelative(-80f)
				horizontalLineToRelative(320f)
				verticalLineToRelative(80f)
				lineTo(600f, 520f)
				close()
			}
		}.build()

		return _IconTextDecrease!!
	}

@Suppress("ObjectPropertyName")
private var _IconTextDecrease: ImageVector? = null
