package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable

/**
 * Writes plain text to the system clipboard. Compose's own clipboard entries are platform-typed,
 * so the seam sits here rather than at a shared call site.
 */
@Composable
expect fun rememberClipboardCopier(): (String) -> Unit
