package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val EditorIcons.IconTextReset: ImageVector
	get() {
		if (_IconTextReset != null) {
			return _IconTextReset!!
		}
		_IconTextReset = ImageVector.Builder(
			name = "IconTextReset",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 960f,
			viewportHeight = 960f
		).apply {
			path(fill = SolidColor(Color.Black)) {
				moveTo(420f, 800f)
				verticalLineToRelative(-520f)
				lineTo(200f, 280f)
				verticalLineToRelative(-120f)
				horizontalLineToRelative(560f)
				verticalLineToRelative(120f)
				lineTo(540f, 280f)
				verticalLineToRelative(520f)
				lineTo(420f, 800f)
				close()
			}
		}.build()

		return _IconTextReset!!
	}

@Suppress("ObjectPropertyName")
private var _IconTextReset: ImageVector? = null
