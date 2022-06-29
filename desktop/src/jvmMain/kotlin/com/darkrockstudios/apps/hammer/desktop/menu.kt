package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.input.key.Key
import com.darkrockstudios.apps.hammer.common.data.KeyShortcut

fun KeyShortcut.toDesktopShortcut(): androidx.compose.ui.input.key.KeyShortcut {
    return androidx.compose.ui.input.key.KeyShortcut(
        key = Key(keyCode),
        ctrl = ctrl,
        alt = alt,
        meta = meta,
        shift = shift
    )
}