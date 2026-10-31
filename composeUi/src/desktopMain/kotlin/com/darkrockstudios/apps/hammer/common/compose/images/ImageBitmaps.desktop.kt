package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.toBitmap

actual fun Image.toImageBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()
