package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val EditorIcons.IconUndo: ImageVector
	get() {
		if (_IconUndo != null) {
			return _IconUndo!!
		}
		_IconUndo = ImageVector.Builder(
			name = "IconUndo",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color(0xFF6F6F6F))) {
				moveTo(12.5f, 8f)
				curveTo(9.85f, 8f, 7.45f, 8.99f, 5.6f, 10.6f)
				lineTo(3.71f, 8.71f)
				curveTo(3.08f, 8.08f, 2f, 8.52f, 2f, 9.41f)
				verticalLineTo(15f)
				curveTo(2f, 15.55f, 2.45f, 16f, 3f, 16f)
				horizontalLineTo(8.59f)
				curveTo(9.48f, 16f, 9.93f, 14.92f, 9.3f, 14.29f)
				lineTo(7.39f, 12.38f)
				curveTo(8.78f, 11.22f, 10.55f, 10.5f, 12.51f, 10.5f)
				curveTo(15.67f, 10.5f, 18.4f, 12.34f, 19.7f, 15f)
				curveTo(19.97f, 15.56f, 20.61f, 15.84f, 21.2f, 15.64f)
				curveTo(21.91f, 15.41f, 22.27f, 14.6f, 21.95f, 13.92f)
				curveTo(20.23f, 10.42f, 16.65f, 8f, 12.5f, 8f)
				close()
			}
		}.build()

		return _IconUndo!!
	}

@Suppress("ObjectPropertyName")
private var _IconUndo: ImageVector? = null
