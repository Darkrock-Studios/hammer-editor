package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.runtime.Composable

/** Taps leave focus alone on iOS; detecting an attached keyboard would need GameController. */
@Composable
internal actual fun hasHardwareKeyboard(): Boolean = false
