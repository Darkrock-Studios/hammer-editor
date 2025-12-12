package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val EditorIcons.IconItalic: ImageVector
	get() {
		if (_IconItalic != null) {
			return _IconItalic!!
		}
		_IconItalic = ImageVector.Builder(
			name = "IconItalic",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color(0xFF6F6F6F))) {
				moveTo(9.667f, 5.714f)
				curveTo(9.667f, 6.663f, 10.448f, 7.429f, 11.417f, 7.429f)
				horizontalLineTo(12.245f)
				lineTo(8.255f, 16.571f)
				horizontalLineTo(6.75f)
				curveTo(5.782f, 16.571f, 5f, 17.337f, 5f, 18.286f)
				curveTo(5f, 19.234f, 5.782f, 20f, 6.75f, 20f)
				horizontalLineTo(12.583f)
				curveTo(13.552f, 20f, 14.333f, 19.234f, 14.333f, 18.286f)
				curveTo(14.333f, 17.337f, 13.552f, 16.571f, 12.583f, 16.571f)
				horizontalLineTo(11.755f)
				lineTo(15.745f, 7.429f)
				horizontalLineTo(17.25f)
				curveTo(18.218f, 7.429f, 19f, 6.663f, 19f, 5.714f)
				curveTo(19f, 4.766f, 18.218f, 4f, 17.25f, 4f)
				horizontalLineTo(11.417f)
				curveTo(10.448f, 4f, 9.667f, 4.766f, 9.667f, 5.714f)
				close()
			}
		}.build()

		return _IconItalic!!
	}

@Suppress("ObjectPropertyName")
private var _IconItalic: ImageVector? = null
