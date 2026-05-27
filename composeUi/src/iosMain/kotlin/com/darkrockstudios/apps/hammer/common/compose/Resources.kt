package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
actual fun painterResource(res: String, drawableKlass: Any?): Painter =
	error("String-based painterResource is not supported on iOS; use org.jetbrains.compose.resources.painterResource(DrawableResource) instead")
