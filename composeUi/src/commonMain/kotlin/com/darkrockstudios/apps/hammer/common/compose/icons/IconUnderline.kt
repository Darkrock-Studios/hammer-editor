package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val EditorIcons.IconUnderline: ImageVector
	get() {
		if (_IconUnderline != null) {
			return _IconUnderline!!
		}
		_IconUnderline = ImageVector.Builder(
			name = "IconUnderline",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color(0xFF6F6F6F))) {
				moveTo(12.79f, 16.95f)
				curveTo(15.82f, 16.56f, 18f, 13.84f, 18f, 10.79f)
				verticalLineTo(4.25f)
				curveTo(18f, 3.56f, 17.44f, 3f, 16.75f, 3f)
				curveTo(16.06f, 3f, 15.5f, 3.56f, 15.5f, 4.25f)
				verticalLineTo(10.9f)
				curveTo(15.5f, 12.57f, 14.37f, 14.09f, 12.73f, 14.42f)
				curveTo(10.48f, 14.89f, 8.5f, 13.17f, 8.5f, 11f)
				verticalLineTo(4.25f)
				curveTo(8.5f, 3.56f, 7.94f, 3f, 7.25f, 3f)
				curveTo(6.56f, 3f, 6f, 3.56f, 6f, 4.25f)
				verticalLineTo(11f)
				curveTo(6f, 14.57f, 9.13f, 17.42f, 12.79f, 16.95f)
				close()
				moveTo(5f, 20f)
				curveTo(5f, 20.55f, 5.45f, 21f, 6f, 21f)
				horizontalLineTo(18f)
				curveTo(18.55f, 21f, 19f, 20.55f, 19f, 20f)
				curveTo(19f, 19.45f, 18.55f, 19f, 18f, 19f)
				horizontalLineTo(6f)
				curveTo(5.45f, 19f, 5f, 19.45f, 5f, 20f)
				close()
			}
		}.build()

		return _IconUnderline!!
	}

@Suppress("ObjectPropertyName")
private var _IconUnderline: ImageVector? = null
