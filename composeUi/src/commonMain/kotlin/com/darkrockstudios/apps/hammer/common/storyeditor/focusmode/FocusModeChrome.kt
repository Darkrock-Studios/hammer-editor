package com.darkrockstudios.apps.hammer.common.storyeditor.focusmode

import androidx.compose.ui.Modifier

/**
 * Insets focus-mode content below any window chrome that overlays the top of the full-screen
 * dialog. On desktop this clears the custom title bar and native window controls so the close
 * button is reachable; on mobile it is a no-op.
 */
expect fun Modifier.focusModeChromePadding(): Modifier
