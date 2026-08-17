package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun horizontalResizePointerIcon(): PointerIcon =
	PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
