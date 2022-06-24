package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
actual fun painterResource(res: String, drawableKlass: Any?): Painter =
    androidx.compose.ui.res.painterResource(res)