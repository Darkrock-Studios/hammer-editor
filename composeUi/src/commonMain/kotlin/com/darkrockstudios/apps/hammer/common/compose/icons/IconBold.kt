package com.darkrockstudios.apps.hammer.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val EditorIcons.IconBold: ImageVector
	get() {
		if (_IconBold != null) {
			return _IconBold!!
		}
		_IconBold = ImageVector.Builder(
			name = "IconBold",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(fill = SolidColor(Color(0xFF6F6F6F))) {
				moveTo(16.4f, 11.73f)
				curveTo(17.573f, 10.869f, 18.395f, 9.454f, 18.395f, 8.143f)
				curveTo(18.395f, 5.237f, 16.279f, 3f, 13.558f, 3f)
				horizontalLineTo(7.209f)
				curveTo(6.544f, 3f, 6f, 3.579f, 6f, 4.286f)
				verticalLineTo(19.714f)
				curveTo(6f, 20.421f, 6.544f, 21f, 7.209f, 21f)
				horizontalLineTo(14.199f)
				curveTo(16.702f, 21f, 18.988f, 18.827f, 19f, 16.153f)
				curveTo(19.012f, 14.186f, 17.972f, 12.501f, 16.4f, 11.73f)
				close()
				moveTo(9.628f, 6.214f)
				horizontalLineTo(13.256f)
				curveTo(14.259f, 6.214f, 15.07f, 7.076f, 15.07f, 8.143f)
				curveTo(15.07f, 9.21f, 14.259f, 10.071f, 13.256f, 10.071f)
				horizontalLineTo(9.628f)
				verticalLineTo(6.214f)
				close()
				moveTo(13.86f, 17.786f)
				horizontalLineTo(9.628f)
				verticalLineTo(13.929f)
				horizontalLineTo(13.86f)
				curveTo(14.864f, 13.929f, 15.674f, 14.79f, 15.674f, 15.857f)
				curveTo(15.674f, 16.924f, 14.864f, 17.786f, 13.86f, 17.786f)
				close()
			}
		}.build()

		return _IconBold!!
	}

@Suppress("ObjectPropertyName")
private var _IconBold: ImageVector? = null
