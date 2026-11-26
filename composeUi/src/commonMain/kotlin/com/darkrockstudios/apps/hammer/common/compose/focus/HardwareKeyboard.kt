package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.runtime.Composable

/** Whether a physical keyboard is attached, so a tap may hand the keyboard a list to drive. */
@Composable
internal expect fun hasHardwareKeyboard(): Boolean
