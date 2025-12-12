package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val EditorIcons.IconRedo: ImageVector
	get() {
		if (_IconRedo != null) {
			return _IconRedo!!
		}
		_IconRedo = ImageVector.Builder(
			name = "IconRedo",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color(0xFF6F6F6F))) {
				moveTo(18.4f, 10.6f)
				curveTo(16.55f, 8.99f, 14.15f, 8f, 11.5f, 8f)
				curveTo(7.34f, 8f, 3.76f, 10.42f, 2.06f, 13.93f)
				curveTo(1.74f, 14.6f, 2.1f, 15.4f, 2.81f, 15.64f)
				curveTo(3.4f, 15.84f, 4.04f, 15.56f, 4.31f, 15f)
				curveTo(5.61f, 12.34f, 8.34f, 10.5f, 11.5f, 10.5f)
				curveTo(13.45f, 10.5f, 15.23f, 11.22f, 16.62f, 12.38f)
				lineTo(14.71f, 14.29f)
				curveTo(14.08f, 14.92f, 14.52f, 16f, 15.41f, 16f)
				horizontalLineTo(21f)
				curveTo(21.55f, 16f, 22f, 15.55f, 22f, 15f)
				verticalLineTo(9.41f)
				curveTo(22f, 8.52f, 20.92f, 8.07f, 20.29f, 8.7f)
				lineTo(18.4f, 10.6f)
				close()
			}
		}.build()

		return _IconRedo!!
	}

@Suppress("ObjectPropertyName")
private var _IconRedo: ImageVector? = null
